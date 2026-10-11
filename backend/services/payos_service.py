"""PayOS payment flow for football bookings.

The browser return/cancel URLs only navigate the app.  A verified PayOS
webhook is the only path that marks a booking as paid.
"""

from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from decimal import Decimal, InvalidOperation
import logging
import os
import re
import secrets
import time
from urllib.parse import parse_qsl, urlencode, urlsplit, urlunsplit
from zoneinfo import ZoneInfo

from firebase_admin import firestore
from google.cloud.firestore_v1.base_query import FieldFilter
from payos import AsyncPayOS
from payos.types import CreatePaymentLinkRequest

from services.firestore_service import get_db

VIETNAM = ZoneInfo("Asia/Ho_Chi_Minh")
ONLINE_HOLD_MINUTES = 15
PAYMENT_REF_PATTERN = re.compile(r"^[a-f0-9]{32}$")
logger = logging.getLogger(__name__)


class PaymentError(Exception):
    def __init__(self, message: str, status: int = 400):
        super().__init__(message)
        self.status = status


@dataclass(frozen=True)
class Settings:
    client_id: str
    api_key: str
    checksum_key: str
    return_url: str
    cancel_url: str
    webhook_url: str

    @classmethod
    def load(cls) -> "Settings":
        # During migration, derive the PayOS public host from the old ngrok
        # return URL if explicit PayOS URLs have not been added yet.
        legacy_url = os.getenv("VNPAY_RETURN_URL", "").strip()
        legacy_parts = urlsplit(legacy_url)

        def public_url(env_name: str, path: str) -> str:
            configured = os.getenv(env_name, "").strip()
            if configured:
                return configured
            if legacy_parts.scheme == "https" and legacy_parts.hostname:
                return urlunsplit((legacy_parts.scheme, legacy_parts.netloc, path, "", ""))
            return ""

        config = cls(
            client_id=os.getenv("PAYOS_CLIENT_ID", "").strip(),
            api_key=os.getenv("PAYOS_API_KEY", "").strip(),
            checksum_key=os.getenv("PAYOS_CHECKSUM_KEY", "").strip(),
            return_url=public_url("PAYOS_RETURN_URL", "/payments/payos/return"),
            cancel_url=public_url("PAYOS_CANCEL_URL", "/payments/payos/cancel"),
            webhook_url=public_url("PAYOS_WEBHOOK_URL", "/payments/payos/webhook"),
        )
        for value in (config.return_url, config.cancel_url, config.webhook_url):
            parsed = urlsplit(value)
            if (parsed.scheme != "https" or not parsed.hostname or parsed.username
                    or parsed.password or parsed.query or parsed.fragment):
                raise PaymentError("PayOS chưa được cấu hình URL HTTPS công khai.", 503)
        if not config.client_id or not config.api_key or not config.checksum_key:
            raise PaymentError("PayOS chưa được cấu hình đầy đủ.", 503)
        return config


def get_payos_client(config: Settings) -> AsyncPayOS:
    return AsyncPayOS(
        client_id=config.client_id,
        api_key=config.api_key,
        checksum_key=config.checksum_key,
    )


def amount_vnd(value) -> int:
    try:
        amount = Decimal(str(value))
        if (not amount.is_finite() or amount <= 0
                or amount != amount.to_integral_value()):
            raise ValueError()
        return int(amount)
    except (InvalidOperation, TypeError, ValueError):
        raise PaymentError("Số tiền đặt sân không hợp lệ.") from None


def authorize(uid: str, user: dict | None, booking: dict | None) -> None:
    if not user or user.get("role") != "customer" or user.get("status", "active") != "active":
        raise PaymentError("Tài khoản không được phép thanh toán.", 403)
    if not booking or booking.get("customerId") != uid:
        raise PaymentError("Không tìm thấy đơn đặt sân.", 404)


def _aware(value: datetime) -> datetime:
    if value.tzinfo is None:
        return value.replace(tzinfo=timezone.utc)
    return value


def _append_query(url: str, values: dict[str, str]) -> str:
    parts = urlsplit(url)
    query = dict(parse_qsl(parts.query, keep_blank_values=True))
    query.update(values)
    return urlunsplit((parts.scheme, parts.netloc, parts.path, urlencode(query), parts.fragment))


def _new_order_code() -> int:
    # PayOS accepts an integer orderCode. Milliseconds plus a small random
    # suffix avoids collisions when two customers start payment together.
    return int(f"{int(time.time() * 1000)}{secrets.randbelow(1000):03d}")


def _session_result(session: dict, booking: dict, include_url: bool = False) -> dict:
    status = session.get("status", "pending")
    transaction_id = session.get("payosTransactionId")
    if status == "paid" and (
            booking.get("paymentReviewRequired")
            or booking.get("bookingStatus") not in {"pending", "approved"}
            or booking.get("paymentStatus") != "paid"
            or booking.get("payosTransactionId") != transaction_id):
        status = "review"
    elif status == "pending" and booking.get("paymentStatus") != "unpaid":
        status = "review"
    elif status == "pending" and booking.get("bookingStatus") not in {"pending", "approved"}:
        status = "cancelled"
    elif status == "pending" and _aware(session["expiresAt"]) <= datetime.now(timezone.utc):
        status = "expired"

    result = {
        "bookingId": session["bookingId"],
        "paymentRef": session["paymentRef"],
        "status": status,
        "amount": session["amountVnd"],
        "expiresAt": _aware(session["expiresAt"]).isoformat(),
        "bookingStatus": booking.get("bookingStatus"),
    }
    if include_url and status == "pending":
        for field in ("paymentUrl", "checkoutUrl", "qrCode", "accountName", "accountNumber"):
            if session.get(field):
                result[field] = session[field]
    return result


def _reserve_session(uid: str, booking_id: str):
    db = get_db()
    booking_ref = db.collection("bookings").document(booking_id)
    user_ref = db.collection("users").document(uid)
    payment_ref = secrets.token_hex(16)
    session_ref = db.collection("payment_sessions").document(payment_ref)
    order_code = _new_order_code()

    @firestore.transactional
    def reserve(tx):
        user = user_ref.get(transaction=tx).to_dict()
        booking = booking_ref.get(transaction=tx).to_dict()
        authorize(uid, user, booking)

        existing_id = booking.get("paymentSessionId")
        if existing_id:
            existing_ref = db.collection("payment_sessions").document(existing_id)
            existing = existing_ref.get(transaction=tx).to_dict()
            if existing and existing.get("bookingId") == booking_id and existing.get("customerId") == uid:
                if existing.get("status") == "paid":
                    return existing, booking, False
                if existing.get("status") == "pending" and existing.get("paymentUrl"):
                    return existing, booking, False

        if (booking.get("bookingStatus") not in {"pending", "approved"}
                or booking.get("paymentStatus") != "unpaid"):
            raise PaymentError("Đơn không còn ở trạng thái có thể thanh toán.", 409)

        try:
            starts = datetime.strptime(
                booking["bookingDate"] + " " + booking["startTime"],
                "%Y-%m-%d %H:%M",
            ).replace(tzinfo=VIETNAM)
        except (KeyError, TypeError, ValueError):
            raise PaymentError("Thời gian đơn không hợp lệ.", 409) from None

        now = datetime.now(timezone.utc).replace(microsecond=0)
        if starts <= now:
            raise PaymentError("Đơn đã đến giờ thi đấu.", 409)
        expires = min(now + timedelta(minutes=ONLINE_HOLD_MINUTES), starts)
        if expires <= now:
            raise PaymentError("Đã hết thời gian giữ chỗ thanh toán online.", 409)

        amount = amount_vnd(booking.get("totalAmount"))
        session = {
            "bookingId": booking_id,
            "customerId": uid,
            "paymentRef": payment_ref,
            "provider": "payos",
            "payosOrderCode": order_code,
            "amountVnd": amount,
            "status": "pending",
            "createdAt": now,
            "expiresAt": expires,
        }
        tx.create(session_ref, session)
        tx.update(booking_ref, {
            "paymentSessionId": payment_ref,
            "updatedAt": firestore.SERVER_TIMESTAMP,
        })
        return session, booking, True

    return reserve(db.transaction())


async def create_session(uid: str, booking_id: str) -> dict:
    config = Settings.load()
    session, booking, is_new = _reserve_session(uid, booking_id)
    if not is_new:
        return _session_result(session, booking, include_url=True)

    payment_ref = session["paymentRef"]
    return_url = _append_query(config.return_url, {"payment_ref": payment_ref})
    cancel_url = _append_query(config.cancel_url, {"payment_ref": payment_ref})
    description = "Dat san " + payment_ref[:16]
    request = CreatePaymentLinkRequest(
        orderCode=session["payosOrderCode"],
        amount=session["amountVnd"],
        description=description,
        returnUrl=return_url,
        cancelUrl=cancel_url,
        expiredAt=int(_aware(session["expiresAt"]).timestamp()),
    )

    try:
        response = await get_payos_client(config).payment_requests.create(request)
    except Exception as exc:
        logger.exception("PayOS create payment link failed: %s", type(exc).__name__)
        get_db().collection("payment_sessions").document(payment_ref).update({
            "status": "failed",
            "failureReason": "payos_create_failed",
            "updatedAt": firestore.SERVER_TIMESTAMP,
        })
        raise PaymentError("Không tạo được phiên thanh toán PayOS. Vui lòng thử lại.", 503) from exc

    if response.order_code != session["payosOrderCode"] or response.amount != session["amountVnd"]:
        raise PaymentError("PayOS trả về thông tin đơn không khớp.", 502)

    update = {
        "paymentUrl": response.checkout_url,
        "checkoutUrl": response.checkout_url,
        "qrCode": response.qr_code,
        "accountName": response.account_name,
        "accountNumber": response.account_number,
        "payosPaymentLinkId": response.payment_link_id,
        "payosStatus": response.status,
        "updatedAt": firestore.SERVER_TIMESTAMP,
    }
    get_db().collection("payment_sessions").document(payment_ref).update(update)
    session.update({key: value for key, value in update.items() if key != "updatedAt"})
    return _session_result(session, booking, include_url=True)


def payment_status(uid: str, payment_ref: str) -> dict:
    if not PAYMENT_REF_PATTERN.fullmatch(payment_ref):
        raise PaymentError("Mã thanh toán không hợp lệ.")
    db = get_db()

    @firestore.transactional
    def read(tx):
        session = db.collection("payment_sessions").document(payment_ref).get(transaction=tx).to_dict()
        if not session or session.get("customerId") != uid:
            raise PaymentError("Không tìm thấy giao dịch.", 404)
        user = db.collection("users").document(uid).get(transaction=tx).to_dict()
        booking = db.collection("bookings").document(session["bookingId"]).get(transaction=tx).to_dict()
        authorize(uid, user, booking)
        return _session_result(session, booking, include_url=True)

    return read(db.transaction())


def _parse_paid_at(value: str) -> datetime:
    try:
        return datetime.strptime(value, "%Y-%m-%d %H:%M:%S").replace(tzinfo=VIETNAM)
    except (TypeError, ValueError):
        return datetime.now(timezone.utc)


async def settle_webhook(payload: dict) -> dict:
    config = Settings.load()
    try:
        webhook = await get_payos_client(config).webhooks.verify(payload)
    except Exception as exc:
        raise PaymentError("Webhook PayOS không hợp lệ.", 400) from exc

    order_code = int(webhook.order_code)
    db = get_db()
    matches = list(db.collection("payment_sessions")
                   .where(filter=FieldFilter("payosOrderCode", "==", order_code))
                   .limit(1).stream())
    if not matches:
        # PayOS sends a sample payload when a webhook URL is confirmed. It is
        # valid but does not belong to one of this application's sessions.
        return {"status": "ignored", "reason": "unknown_order"}
    session_ref = matches[0].reference

    @firestore.transactional
    def settle(tx):
        session = session_ref.get(transaction=tx).to_dict()
        if not session or session.get("payosOrderCode") != order_code:
            raise PaymentError("Phiên thanh toán không khớp.", 409)
        if webhook.amount != session.get("amountVnd"):
            raise PaymentError("Số tiền webhook không khớp.", 400)
        if (session.get("payosPaymentLinkId")
                and webhook.payment_link_id != session.get("payosPaymentLinkId")):
            raise PaymentError("Payment link PayOS không khớp.", 400)

        booking_ref = db.collection("bookings").document(session["bookingId"])
        booking = booking_ref.get(transaction=tx).to_dict()
        if (not booking or booking.get("paymentSessionId") != session_ref.id
                or booking.get("customerId") != session["customerId"]):
            raise PaymentError("Đơn đặt sân không khớp phiên thanh toán.", 409)

        transaction_id = webhook.reference
        if session.get("status") == "paid":
            if session.get("payosTransactionId") != transaction_id:
                raise PaymentError("Giao dịch PayOS bị trùng hoặc xung đột.", 409)
            return {"status": "paid", "paymentRef": session_ref.id}

        if webhook.code != "00":
            tx.update(session_ref, {
                "status": "failed",
                "payosStatus": webhook.code,
                "failureReason": webhook.desc,
                "updatedAt": firestore.SERVER_TIMESTAMP,
            })
            return {"status": "failed", "paymentRef": session_ref.id}

        if amount_vnd(booking.get("totalAmount")) != session["amountVnd"]:
            raise PaymentError("Tổng tiền booking đã thay đổi.", 409)

        owns_slot = False
        day_ref = None
        if all(isinstance(booking.get(key), str) and booking[key]
               for key in ("pitchId", "bookingDate", "slotId")):
            day_ref = (db.collection("pitches").document(booking["pitchId"])
                       .collection("availability").document(booking["bookingDate"]))
            day = day_ref.get(transaction=tx).to_dict() or {}
            lock = day.get("slots", {}).get(booking["slotId"], {})
            owns_slot = (lock.get("bookingId") == session["bookingId"]
                         and lock.get("status") in {"pending", "booked"})

        paid_at = _parse_paid_at(webhook.transaction_date_time)
        created_at = _aware(session["createdAt"])
        expires_at = _aware(session["expiresAt"])
        review = (
            not owns_slot
            or booking.get("bookingStatus") not in {"pending", "approved"}
            or booking.get("paymentStatus") != "unpaid"
            or paid_at < created_at
            or paid_at > expires_at
        )
        auto_approved = not review
        tx.update(session_ref, {
            "status": "paid",
            "payosTransactionId": transaction_id,
            "payosPaymentLinkId": webhook.payment_link_id,
            "paidAt": paid_at,
            "payosStatus": webhook.code,
            "autoApproved": auto_approved,
            "updatedAt": firestore.SERVER_TIMESTAMP,
        })
        booking_update = {
            "paymentStatus": "paid",
            "paymentMethod": "payos",
            "payosTransactionId": transaction_id,
            "paymentReviewRequired": review,
            "updatedAt": firestore.SERVER_TIMESTAMP,
        }
        if auto_approved:
            booking_update.update({
                "bookingStatus": "approved",
                "paymentApprovedAt": paid_at,
                "approvalSource": "payos",
            })
            # The hold created during booking becomes a confirmed reservation
            # in the same transaction as the payment and booking update.
            tx.update(day_ref, {
                f"slots.{booking['slotId']}": {
                    "status": "booked",
                    "bookingId": session["bookingId"],
                },
                "lastSlotId": booking["slotId"],
            })
        tx.update(booking_ref, booking_update)
        return {
            "status": "paid",
            "paymentRef": session_ref.id,
            "bookingStatus": "approved" if auto_approved else booking.get("bookingStatus"),
            "autoApproved": auto_approved,
        }

    return settle(db.transaction())


def reconcile_session(uid: str, payment_ref: str) -> dict:
    # PayOS webhook is authoritative; this endpoint only re-reads Firestore.
    return payment_status(uid, payment_ref)
