import logging
import re
from urllib.parse import quote

from fastapi import APIRouter, Depends, Header, HTTPException, Request
from fastapi.responses import JSONResponse, RedirectResponse
from pydantic import BaseModel, ConfigDict, Field

from services.firestore_service import verify_firebase_token
from services.payos_service import PaymentError, Settings, create_session, payment_status, reconcile_session, settle_webhook

router = APIRouter(prefix="/payments/payos", tags=["payments-payos"])
logger = logging.getLogger(__name__)


class PaymentRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")
    bookingId: str = Field(min_length=1, max_length=128, pattern=r"^[A-Za-z0-9_-]+$")


def customer_uid(authorization: str | None = Header(default=None)):
    if not authorization or not authorization.startswith("Bearer "):
        logger.warning("PayOS session request rejected: missing or malformed Authorization header")
        raise HTTPException(401, "Thiếu Firebase ID token")
    try:
        return verify_firebase_token(authorization[7:], check_revoked=True)["uid"]
    except Exception as exc:
        logger.warning(
            "PayOS session request rejected by Firebase: %s: %s",
            type(exc).__name__,
            str(exc)[:160],
        )
        raise HTTPException(401, "Phiên đăng nhập không hợp lệ") from None


def _error(exc: Exception):
    if isinstance(exc, PaymentError):
        logger.warning("PayOS request rejected: %s", str(exc)[:240])
        raise HTTPException(exc.status, str(exc)) from None
    logger.exception("Unhandled PayOS request error: %s", type(exc).__name__)
    raise HTTPException(503, "Chưa kiểm tra được thanh toán. Vui lòng thử lại.") from None


@router.post("/sessions")
async def post_session(payload: PaymentRequest, uid: str = Depends(customer_uid)):
    try:
        return JSONResponse(await create_session(uid, payload.bookingId),
                            headers={"Cache-Control": "no-store"})
    except Exception as exc:
        _error(exc)


@router.get("/sessions/{payment_ref}")
def get_session(payment_ref: str, uid: str = Depends(customer_uid)):
    if not re.fullmatch(r"[a-f0-9]{32}", payment_ref):
        raise HTTPException(400, "Mã thanh toán không hợp lệ")
    try:
        return JSONResponse(payment_status(uid, payment_ref),
                            headers={"Cache-Control": "no-store"})
    except Exception as exc:
        _error(exc)


@router.post("/sessions/{payment_ref}/reconcile")
def reconcile(payment_ref: str, uid: str = Depends(customer_uid)):
    if not re.fullmatch(r"[a-f0-9]{32}", payment_ref):
        raise HTTPException(400, "Mã thanh toán không hợp lệ")
    try:
        return JSONResponse(reconcile_session(uid, payment_ref),
                            headers={"Cache-Control": "no-store"})
    except Exception as exc:
        _error(exc)


@router.post("/webhook")
async def webhook(request: Request):
    try:
        payload = await request.json()
        result = await settle_webhook(payload)
        return JSONResponse(result, headers={"Cache-Control": "no-store"})
    except PaymentError as exc:
        raise HTTPException(exc.status, str(exc)) from None
    except Exception:
        # A 5xx tells PayOS to retry transient Firestore/server failures.
        raise HTTPException(503, "Chưa xử lý được webhook PayOS.") from None


def _callback_redirect(request: Request, result: str):
    payment_ref = request.query_params.get("payment_ref", "")
    if not re.fullmatch(r"[a-f0-9]{32}", payment_ref):
        payment_ref = ""
    query = f"result={quote(result)}"
    if payment_ref:
        query += f"&payment_ref={quote(payment_ref)}"
    return RedirectResponse(
        f"footballbooking://payment_result?{query}",
        status_code=303,
        headers={"Cache-Control": "no-store", "Referrer-Policy": "no-referrer"},
    )


@router.get("/return")
def payment_return(request: Request):
    # This URL is navigation only. The webhook settles the order.
    return _callback_redirect(request, "success")


@router.get("/cancel")
def payment_cancel(request: Request):
    return _callback_redirect(request, "cancel")
