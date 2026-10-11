"""Review creation and pitch-rating aggregation."""

from datetime import datetime
from typing import Any
from zoneinfo import ZoneInfo

from firebase_admin import firestore
from google.cloud.firestore_v1.base_query import FieldFilter

from services.firestore_service import get_db


class ReviewError(Exception):
    def __init__(self, message: str, status: int = 400):
        super().__init__(message)
        self.status = status


VIETNAM_TZ = ZoneInfo("Asia/Ho_Chi_Minh")


def _booking_end_time(booking: dict[str, Any]) -> datetime:
    booking_date = booking.get("bookingDate")
    end_time = booking.get("endTime")
    if not booking_date or not end_time:
        raise ReviewError("Booking thiếu mốc thời gian kết thúc")
    try:
        return datetime.strptime(
            f"{booking_date} {end_time}", "%Y-%m-%d %H:%M"
        ).replace(tzinfo=VIETNAM_TZ)
    except ValueError as exc:
        raise ReviewError("Mốc thời gian booking không hợp lệ") from exc


def create_review(uid: str, booking_id: str, rating: float, comment: str) -> dict[str, Any]:
    if not booking_id or len(booking_id) > 128:
        raise ReviewError("Mã booking không hợp lệ")
    if not 1 <= rating <= 5:
        raise ReviewError("Điểm đánh giá phải từ 1 đến 5 sao")
    if len(comment) > 1000:
        raise ReviewError("Nhận xét không được vượt quá 1000 ký tự")

    db = get_db()
    booking_ref = db.collection("bookings").document(booking_id)
    existing = list(
        db.collection("reviews")
        .where(filter=FieldFilter("bookingId", "==", booking_id))
        .limit(1)
        .stream()
    )
    if existing:
        raise ReviewError("Booking này đã được đánh giá", 409)

    # Dùng bookingId làm reviewId để khóa trùng lặp trong transaction.
    review_ref = db.collection("reviews").document(booking_id)

    @firestore.transactional
    def write_transaction(transaction):
        booking_snapshot = booking_ref.get(transaction=transaction)
        if not booking_snapshot.exists:
            raise ReviewError("Không tìm thấy booking", 404)

        booking = booking_snapshot.to_dict() or {}
        if booking.get("customerId") != uid:
            raise ReviewError("Bạn không có quyền đánh giá booking này", 403)
        if booking.get("bookingStatus") != "approved":
            raise ReviewError("Booking chưa được duyệt")
        if datetime.now(VIETNAM_TZ) < _booking_end_time(booking):
            raise ReviewError("Chỉ có thể đánh giá sau khi hết giờ sân")
        if booking.get("paymentStatus") != "paid":
            raise ReviewError("Chỉ có thể đánh giá booking đã thanh toán")

        duplicate = review_ref.get(transaction=transaction)
        if duplicate.exists:
            raise ReviewError("Booking này đã được đánh giá", 409)

        pitch_id = booking.get("pitchId")
        if not pitch_id:
            raise ReviewError("Booking không có thông tin sân")
        pitch_ref = db.collection("pitches").document(pitch_id)
        pitch_snapshot = pitch_ref.get(transaction=transaction)
        if not pitch_snapshot.exists:
            raise ReviewError("Không tìm thấy sân", 404)

        pitch = pitch_snapshot.to_dict() or {}
        old_count = max(0, int(pitch.get("totalReviews") or 0))
        old_rating = float(pitch.get("rating") or 0)
        new_count = old_count + 1
        new_rating = ((old_rating * old_count) + rating) / new_count
        new_rating = max(0.0, min(5.0, new_rating))

        review = {
            "reviewId": booking_id,
            "bookingId": booking_id,
            "customerId": uid,
            "customerName": booking.get("customerName") or "Người dùng",
            "customerAvatar": booking.get("customerAvatar") or "",
            "pitchId": pitch_id,
            "rating": float(rating),
            "comment": comment.strip(),
            "imageUrls": [],
            "createdAt": firestore.SERVER_TIMESTAMP,
        }
        transaction.set(review_ref, review)
        if booking.get("matchStatus") != "finished":
            transaction.update(
                booking_ref,
                {
                    "matchStatus": "finished",
                    "updatedAt": firestore.SERVER_TIMESTAMP,
                },
            )
        transaction.update(
            pitch_ref,
            {
                "rating": new_rating,
                "totalReviews": new_count,
                "updatedAt": firestore.SERVER_TIMESTAMP,
            },
        )
        return {
            "reviewId": booking_id,
            "pitchId": pitch_id,
            "rating": new_rating,
            "totalReviews": new_count,
        }

    return write_transaction(db.transaction())
