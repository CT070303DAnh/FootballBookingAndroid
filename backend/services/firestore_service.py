import os
import math
import hashlib
import logging
from datetime import date, datetime
from typing import Any
from zoneinfo import ZoneInfo

import firebase_admin
from firebase_admin import auth, credentials, firestore
from google.cloud.firestore_v1.base_query import FieldFilter


DEFAULT_SLOTS = [
    {"slotId": "slot_1", "startTime": "06:00", "endTime": "07:30", "isPeakHour": False, "surcharge": 0},
    {"slotId": "slot_2", "startTime": "07:30", "endTime": "09:00", "isPeakHour": False, "surcharge": 0},
    {"slotId": "slot_3", "startTime": "09:00", "endTime": "10:30", "isPeakHour": False, "surcharge": 0},
    {"slotId": "slot_4", "startTime": "14:00", "endTime": "15:30", "isPeakHour": False, "surcharge": 0},
    {"slotId": "slot_5", "startTime": "15:30", "endTime": "17:00", "isPeakHour": False, "surcharge": 0},
    {"slotId": "slot_6", "startTime": "17:00", "endTime": "18:30", "isPeakHour": True, "surcharge": 30000},
    {"slotId": "slot_7", "startTime": "18:30", "endTime": "20:00", "isPeakHour": True, "surcharge": 50000},
    {"slotId": "slot_8", "startTime": "20:00", "endTime": "21:30", "isPeakHour": True, "surcharge": 50000},
    {"slotId": "slot_9", "startTime": "21:30", "endTime": "23:00", "isPeakHour": False, "surcharge": 0},
]

logger = logging.getLogger(__name__)

def init_firebase():
    if not firebase_admin._apps:
        credentials_path = os.getenv("GOOGLE_APPLICATION_CREDENTIALS")
        credential = credentials.Certificate(credentials_path) if credentials_path else None
        firebase_admin.initialize_app(credential)


def get_db():
    init_firebase()
    return firestore.client()


def verify_firebase_token(token: str, *, check_revoked: bool = False) -> dict[str, Any]:
    if not token:
        raise ValueError("Thiếu Firebase ID token")
    try:
        init_firebase()
        return auth.verify_id_token(token, check_revoked=check_revoked)
    except auth.ExpiredIdTokenError as exc:
        logger.warning("Firebase ID token expired")
        raise ValueError("Firebase ID token đã hết hạn") from exc
    except auth.RevokedIdTokenError as exc:
        logger.warning("Firebase ID token revoked")
        raise ValueError("Firebase ID token đã bị thu hồi") from exc
    except auth.UserDisabledError as exc:
        logger.warning("Firebase user is disabled")
        raise ValueError("Tài khoản Firebase đã bị vô hiệu hóa") from exc
    except auth.CertificateFetchError as exc:
        logger.exception("Unable to fetch Firebase public certificates")
        raise ValueError("Backend không lấy được chứng thư Firebase") from exc
    except auth.InvalidIdTokenError as exc:
        logger.warning("Firebase ID token invalid (%s): %s", type(exc).__name__, str(exc)[:200])
        raise ValueError("Firebase ID token không hợp lệ") from exc
    except Exception as exc:
        logger.exception("Unexpected Firebase ID token verification failure: %s", type(exc).__name__)
        raise ValueError("Firebase ID token không hợp lệ") from exc


def get_user(uid: str) -> dict[str, Any]:
    snapshot = get_db().collection("users").document(uid).get()
    if not snapshot.exists:
        raise PermissionError("Không tìm thấy hồ sơ người dùng")
    return {"uid": uid, **(snapshot.to_dict() or {})}


def list_available_pitches() -> list[dict[str, Any]]:
    results = []
    for document in get_db().collection("pitches").where(
        filter=FieldFilter("status", "==", "available")
    ).stream():
        data = document.to_dict() or {}
        results.append({**data, "id": data.get("pitchId") or document.id})
    return results


def get_pitch(pitch_id: str) -> dict[str, Any] | None:
    snapshot = get_db().collection("pitches").document(pitch_id).get()
    if not snapshot.exists:
        return None
    data = snapshot.to_dict() or {}
    return {**data, "id": data.get("pitchId") or snapshot.id}


def get_active_services() -> list[dict[str, Any]]:
    results = []
    for document in get_db().collection("services").where(
        filter=FieldFilter("isActive", "==", True)
    ).stream():
        data = document.to_dict() or {}
        results.append({**data, "id": data.get("serviceId") or document.id})
    return results


def get_slots(pitch_id: str, booking_date: str) -> list[dict[str, Any]]:
    db = get_db()
    pitch = db.collection("pitches").document(pitch_id).get().to_dict() or {}
    bookable = pitch.get("status") == "available" and pitch.get("bookingPolicyVersion") == 2
    snapshots = (
        db.collection("pitches").document(pitch_id).collection("timeSlots").stream()
    )
    slots = []
    has_configured_slots = False
    for document in snapshots:
        has_configured_slots = True
        data = document.to_dict() or {}
        fixed = next((item for item in DEFAULT_SLOTS if item["slotId"] == document.id), None)
        if fixed and data.get("isActive", False) \
                and data.get("startTime") == fixed["startTime"] \
                and data.get("endTime") == fixed["endTime"]:
            slots.append({**fixed, **data, "slotId": document.id,
                          "startTime": fixed["startTime"], "endTime": fixed["endTime"]})
    if not has_configured_slots and pitch.get("useDefaultSlots", False):
        slots = [dict(slot) for slot in DEFAULT_SLOTS]

    availability = (
        db.collection("pitches").document(pitch_id).collection("availability")
        .document(booking_date).get().to_dict() or {}
    )
    availability_slots = availability.get("slots", {})
    bookings = db.collection("bookings").where(
        filter=FieldFilter("pitchId", "==", pitch_id)
    ).where(
        filter=FieldFilter("bookingDate", "==", booking_date)
    ).stream()
    booked_ids = set()
    booked_starts = set()
    for booking in bookings:
        data = booking.to_dict() or {}
        if data.get("bookingStatus") not in {"cancelled", "rejected"}:
            if data.get("slotId"):
                booked_ids.add(str(data["slotId"]))
            if data.get("startTime"):
                booked_starts.add(str(data["startTime"]))

    local_now = datetime.now(ZoneInfo("Asia/Ho_Chi_Minh"))
    today = local_now.date().isoformat()
    current_time = local_now.strftime("%H:%M")
    for slot in slots:
        slot_id = str(slot.get("slotId", ""))
        availability_status = (availability_slots.get(slot_id) or {}).get("status")
        start_time = str(slot.get("startTime") or "")
        is_past = booking_date < today or (booking_date == today and start_time and start_time <= current_time)
        slot["available"] = (
            bookable and not is_past
            and slot_id not in booked_ids
            and slot.get("startTime") not in booked_starts
            and availability_status in {None, "available"}
        )
        slot["status"] = "available" if slot["available"] else "booked"

    return sorted(slots, key=lambda slot: str(slot.get("startTime", "")))


def list_user_bookings(uid: str, limit: int = 20) -> list[dict[str, Any]]:
    query = (
        get_db().collection("bookings").where(filter=FieldFilter("customerId", "==", uid))
        .order_by("createdAt", direction=firestore.Query.DESCENDING).limit(limit)
    )
    results = []
    for document in query.stream():
        data = document.to_dict() or {}
        results.append({"bookingId": document.id, **data})
    return results


class BookingValidationError(ValueError):
    """Safe booking validation message that may be shown to the customer."""


def create_booking_transactionally(uid: str, pending: dict[str, Any]) -> dict[str, Any]:
    db = get_db()
    # Validate again inside the transaction, including retries at the start-time boundary.
    start_at = datetime.strptime(pending["date"] + " " + pending["startTime"], "%Y-%m-%d %H:%M").replace(tzinfo=ZoneInfo("Asia/Ho_Chi_Minh"))
    if len(pending["services"]) > 5:
        raise BookingValidationError("Mỗi đơn hỗ trợ tối đa 5 dịch vụ.")
    service_ids = [service["serviceId"] for service in pending["services"]]
    if len(set(service_ids)) != len(service_ids):
        raise BookingValidationError("Không thể chọn trùng dịch vụ.")

    user_ref = db.collection("users").document(uid)
    pitch_ref = db.collection("pitches").document(pending["pitchId"])
    slot_id = pending["slotId"]
    slot_ref = pitch_ref.collection("timeSlots").document(slot_id)
    booking_date = pending["date"]
    availability_ref = (
        db.collection("pitches").document(pending["pitchId"])
        .collection("availability").document(booking_date)
    )
    confirmation_id = pending.get("confirmationId")
    # A retry after an uncertain commit must address the same booking.
    booking_ref = db.collection("bookings").document(
        "booking_" + hashlib.sha256((uid + ":" + confirmation_id).encode()).hexdigest()
    ) if confirmation_id else db.collection("bookings").document()
    history_ref = db.collection("users").document(uid).collection("bookingHistory").document(booking_ref.id)
    fcm_ref = db.collection("fcm_queue").document()
    transaction = db.transaction()

    @firestore.transactional
    def commit(tx):
        if confirmation_id:
            saved = booking_ref.get(transaction=tx).to_dict()
            if saved:
                if saved.get("customerId") != uid:
                    raise PermissionError("Đơn đặt sân không thuộc tài khoản này.")
                return saved
        if start_at <= datetime.now(ZoneInfo("Asia/Ho_Chi_Minh")):
            raise BookingValidationError("Slot đã qua giờ, không thể đặt.")
        # Transaction.get(DocumentReference) returns an iterator in the Python SDK.
        user_snapshot = user_ref.get(transaction=tx)
        pitch_snapshot = pitch_ref.get(transaction=tx)
        slot_snapshot = slot_ref.get(transaction=tx)
        availability_snapshot = availability_ref.get(transaction=tx)
        service_snapshots = [
            db.collection("services").document(service["serviceId"]).get(transaction=tx)
            for service in pending["services"]
        ]
        existing = list(tx.get(
            db.collection("bookings")
            .where(filter=FieldFilter("pitchId", "==", pending["pitchId"]))
            .where(filter=FieldFilter("bookingDate", "==", booking_date))
        ))

        if not user_snapshot.exists:
            raise PermissionError("Không tìm thấy hồ sơ người dùng")
        user = user_snapshot.to_dict() or {}
        if user.get("role") != "customer":
            raise PermissionError("Chỉ tài khoản khách hàng mới được đặt sân")
        if user.get("status", "active") != "active":
            raise PermissionError("Tài khoản đã bị khóa")
        if not pitch_snapshot.exists:
            raise BookingValidationError("Không tìm thấy sân.")
        pitch = pitch_snapshot.to_dict() or {}
        if pitch.get("status") != "available":
            raise BookingValidationError("Sân không còn khả dụng.")
        if pitch.get("bookingPolicyVersion") != 2:
            raise BookingValidationError("Lịch sân đang chờ kiểm tra dữ liệu. Vui lòng thử lại sau.")

        slot = slot_snapshot.to_dict() if slot_snapshot.exists else next(
            (item for item in DEFAULT_SLOTS if item["slotId"] == slot_id), None
        )
        if not slot or (not slot_snapshot.exists and not pitch.get("useDefaultSlots", False)) or (slot_snapshot.exists and not slot.get("isActive", False)):
            raise BookingValidationError("Slot không còn tồn tại hoặc đã bị tắt.")
        fixed_slot = next((item for item in DEFAULT_SLOTS if item["slotId"] == slot_id), None)
        if not fixed_slot or slot.get("startTime") != fixed_slot["startTime"] or slot.get("endTime") != fixed_slot["endTime"]:
            raise BookingValidationError("Slot phải thuộc lịch cố định 90 phút.")
        if pending["startTime"] != fixed_slot["startTime"] or pending["endTime"] != fixed_slot["endTime"]:
            raise BookingValidationError("Thông tin slot đã thay đổi. Hãy gửi yêu cầu đặt lại.")

        availability = availability_snapshot.to_dict() if availability_snapshot.exists else {}
        slot_state = (availability or {}).get("slots", {}).get(slot_id, {})
        if slot_state.get("status", "available") != "available":
            raise BookingValidationError("Khung giờ vừa được người khác đặt. Hãy tìm slot khác.")

        for document in existing:
            data = document.to_dict() or {}
            if data.get("slotId") == slot_id and data.get("bookingStatus") not in {"cancelled", "rejected"}:
                raise BookingValidationError("Khung giờ vừa được người khác đặt. Hãy tìm slot khác.")

        current_services = []
        for service_snapshot in service_snapshots:
            if not service_snapshot.exists:
                raise BookingValidationError("Dịch vụ đã bị xóa. Hãy chọn lại dịch vụ.")
            service = service_snapshot.to_dict() or {}
            if not service.get("isActive", False):
                raise BookingValidationError("Dịch vụ không còn khả dụng.")
            current_services.append({
                "serviceId": service_snapshot.id,
                "serviceName": service.get("name", ""),
                "price": float(service.get("price", 0) or 0),
            })

        base_price = float(pitch.get("basePrice", 0) or 0)
        surcharge = float(slot.get("surcharge", 0) or 0) if slot.get("isPeakHour", False) else 0.0
        service_total = sum(service["price"] for service in current_services)
        total_amount = base_price + surcharge + service_total
        if not all(math.isfinite(value) and value >= 0 for value in [base_price, surcharge, service_total, total_amount, *(s["price"] for s in current_services)]):
            raise BookingValidationError("Giá sân hoặc dịch vụ không hợp lệ.")
        if not math.isfinite(float(pending["totalAmount"])) or abs(total_amount - float(pending["totalAmount"])) > 0.001:
            raise BookingValidationError("Giá đã thay đổi sau khi bạn xem xác nhận. Hãy tạo yêu cầu mới.")

        now = firestore.SERVER_TIMESTAMP
        booking = {
            "bookingId": booking_ref.id,
            "customerId": uid,
            "customerName": user.get("displayName", "Khách hàng"),
            "customerPhone": user.get("phoneNumber", ""),
            "pitchId": pending["pitchId"],
            "pitchOwnerId": pitch.get("ownerId", ""),
            "pitchName": pitch.get("name", ""),
            "pitchAddress": pitch.get("address", ""),
            "pitchType": str(pitch.get("type", "")),
            "bookingDate": booking_date,
            "slotId": slot_id,
            "startTime": pending["startTime"],
            "endTime": pending["endTime"],
            "services": current_services,
            "basePrice": base_price,
            "surcharge": surcharge,
            "totalServicePrice": service_total,
            "totalAmount": total_amount,
            "paymentStatus": "unpaid",
            "paymentMethod": "pay_later",
            "bookingStatus": "pending",
            "matchStatus": "upcoming",
            "note": "",
            "createdAt": now,
            "updatedAt": now,
        }
        tx.create(booking_ref, booking)
        tx.set(availability_ref, {
            "date": booking_date,
            "lastSlotId": slot_id,
            "slots": {slot_id: {"status": "pending", "bookingId": booking_ref.id}},
        }, merge=True)
        tx.set(history_ref, {"bookingRef": booking_ref.id})
        tx.set(fcm_ref, {
            "type": "new_booking",
            "bookingId": booking_ref.id,
            "customerId": uid,
            "customerName": booking["customerName"],
            "pitchName": booking["pitchName"],
            "time": booking["startTime"],
            "date": booking_date,
            "ownerId": booking["pitchOwnerId"],
            "processed": False,
            "createdAt": now,
        })
        return {"bookingId": booking_ref.id, **booking}

    return commit(transaction)
