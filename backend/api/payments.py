import re
import logging

from fastapi import APIRouter, Depends, Header, HTTPException, Request
from fastapi.responses import JSONResponse, RedirectResponse
from pydantic import BaseModel, ConfigDict, Field

from services.firestore_service import verify_firebase_token
from services.payment_service import Settings, PaymentError, create_session, payment_status, receive_ipn, verified_callback
from services.payment_reconciliation import reconcile_session, reconcile_verified_return

router = APIRouter(prefix="/payments/vnpay", tags=["payments"])
logger = logging.getLogger(__name__)


class PaymentRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")
    bookingId: str = Field(min_length=1, max_length=128, pattern=r"^[A-Za-z0-9_-]+$")


def customer_uid(authorization: str | None = Header(default=None)):
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(401, "Thiếu Firebase ID token")
    try:
        return verify_firebase_token(authorization[7:], check_revoked=True)["uid"]
    except Exception:
        raise HTTPException(401, "Phiên đăng nhập không hợp lệ") from None


def response_for(operation):
    try:
        return JSONResponse(operation(), headers={"Cache-Control": "no-store"})
    except PaymentError as exc:
        raise HTTPException(exc.status, str(exc)) from None
    except Exception:
        # Do not return/log payment URLs, ID tokens, signatures or credentials.
        raise HTTPException(503, "Chưa kiểm tra được thanh toán. Vui lòng thử lại.") from None


@router.post("/sessions")
def post_session(payload: PaymentRequest, request: Request, uid: str = Depends(customer_uid)):
    return response_for(lambda: create_session(uid, payload.bookingId, request.client.host if request.client else ""))


@router.get("/sessions/{payment_ref}")
def get_session(payment_ref: str, uid: str = Depends(customer_uid)):
    if not re.fullmatch(r"[a-f0-9]{32}", payment_ref):
        raise HTTPException(400, "Mã thanh toán không hợp lệ")
    return response_for(lambda: payment_status(uid, payment_ref))


@router.get("/ipn")
def ipn(request: Request):
    try:
        result = receive_ipn(request.query_params.multi_items())
    except Exception:
        # VNPay retries a transient failure; never acknowledge uncommitted settlement.
        result = {"RspCode": "99", "Message": "Retry later"}
    return JSONResponse(result, headers={"Cache-Control": "no-store"})


@router.post("/sessions/{payment_ref}/reconcile")
def reconcile(payment_ref: str, uid: str = Depends(customer_uid)):
    if not re.fullmatch(r"[a-f0-9]{32}", payment_ref):
        raise HTTPException(400, "Mã thanh toán không hợp lệ")
    return response_for(lambda: reconcile_session(uid, payment_ref))


@router.get("/return")
def browser_return(request: Request):
    try:
        params = verified_callback(request.query_params.multi_items(), Settings.load())
    except PaymentError:
        raise HTTPException(400, "Không xác minh được đường dẫn thanh toán") from None
    try:
        # Only the independently signed QueryDR response can settle payment.
        reconcile_verified_return(params)
    except Exception:
        # A timeout must not strand the browser or imply payment failure.
        # Authenticated app retry and the maintenance worker can reconcile later.
        logger.warning("VNPay return reconciliation deferred; retry via app or maintenance worker")
    return RedirectResponse("vnpay://payment_result?payment_ref=" + params["vnp_TxnRef"],
                            status_code=303, headers={"Cache-Control": "no-store", "Referrer-Policy": "no-referrer"})
