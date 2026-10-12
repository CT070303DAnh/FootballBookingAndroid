"""Owner-only cash payment confirmation.

Cash settlement changes financial fields, so it must go through the trusted
backend instead of a direct Android Firestore write.
"""

from datetime import datetime
from typing import Any
from zoneinfo import ZoneInfo

from fastapi import APIRouter, Depends, Header, HTTPException
from pydantic import BaseModel, ConfigDict, Field
from firebase_admin import firestore

from services.firestore_service import get_db, get_user, verify_firebase_token

router = APIRouter(prefix="/payments/cash", tags=["cash-payments"])
VIETNAM_TZ = ZoneInfo("Asia/Ho_Chi_Minh")


class CashConfirmationRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")
    bookingId: str = Field(min_length=1, max_length=128, pattern=r"^[A-Za-z0-9_-]+$")


def owner_uid(authorization: str | None = Header(default=None)) -> str:
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(401, "Thiếu Firebase ID token")
    try:
        claims = verify_firebase_token(authorization[7:], check_revoked=True)
        profile = get_user(claims["uid"])
    except Exception:
        raise HTTPException(401, "Phiên đăng nhập không hợp lệ") from None
    if profile.get("role") != "owner" or profile.get("status", "active") != "active":
        raise HTTPException(403, "Chỉ chủ sân đang hoạt động mới được xác nhận tiền mặt")
    return claims["uid"]


def _booking_end(booking: dict[str, Any]) -> datetime:
    try:
        return datetime.strptime(
            f"{booking['bookingDate']} {booking['endTime']}",
            "%Y-%m-%d %H:%M",
        ).replace(tzinfo=VIETNAM_TZ)
    except (KeyError, TypeError, ValueError) as exc:
        raise ValueError("Mốc thời gian booking không hợp lệ") from exc


def confirm_cash_payment(uid: str, booking_id: str) -> dict[str, Any]:
    db = get_db()
    booking_ref = db.collection("bookings").document(booking_id)

    @firestore.transactional
    def settle(transaction):
        snapshot = booking_ref.get(transaction=transaction)
        if not snapshot.exists:
            raise HTTPException(404, "Không tìm thấy booking")
        booking = snapshot.to_dict() or {}
        if booking.get("pitchOwnerId") != uid:
            raise HTTPException(403, "Booking không thuộc sân của bạn")
        if booking.get("paymentMethod") != "cash":
            raise HTTPException(400, "Booking này không thanh toán tiền mặt")
        if booking.get("paymentStatus") == "paid":
            return {"ok": True, "bookingId": booking_id, "paymentStatus": "paid"}
        if booking.get("paymentStatus") != "unpaid":
            raise HTTPException(400, "Trạng thái thanh toán không hợp lệ")
        if booking.get("bookingStatus") != "approved":
            raise HTTPException(400, "Booking chưa được duyệt")
        if datetime.now(VIETNAM_TZ) < _booking_end(booking):
            raise HTTPException(400, "Chỉ xác nhận tiền sau khi hết giờ sân")

        transaction.update(booking_ref, {
            "paymentStatus": "paid",
            "paymentConfirmedBy": uid,
            "paymentConfirmedAt": firestore.SERVER_TIMESTAMP,
            "updatedAt": firestore.SERVER_TIMESTAMP,
        })
        return {"ok": True, "bookingId": booking_id, "paymentStatus": "paid"}

    return settle(db.transaction())


@router.post("/confirm")
def confirm(payload: CashConfirmationRequest, uid: str = Depends(owner_uid)):
    return confirm_cash_payment(uid, payload.bookingId)
