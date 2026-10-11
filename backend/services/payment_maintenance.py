"""Server-side online hold expiration; independent of Android lifecycle."""
from datetime import datetime, timedelta, timezone
from firebase_admin import firestore
from google.cloud.firestore_v1.base_query import FieldFilter
from services.payment_service import get_db, VIETNAM, ONLINE_HOLD_MINUTES


def online_deadline(booking):
    if booking.get("paymentMethod") not in {"vnpay", "payos"} or not isinstance(booking.get("createdAt"), datetime):
        return None
    try:
        starts = datetime.strptime(booking["bookingDate"] + " " + booking["startTime"],
                                   "%Y-%m-%d %H:%M").replace(tzinfo=VIETNAM)
        return min(booking["createdAt"] + timedelta(minutes=ONLINE_HOLD_MINUTES), starts)
    except (KeyError, ValueError, TypeError):
        return None


def expire_online_booking(booking_id, now=None):
    db = get_db()
    now = now or datetime.now(timezone.utc)
    ref = db.collection("bookings").document(booking_id)

    @firestore.transactional
    def expire(tx):
        booking = ref.get(transaction=tx).to_dict()
        if not booking or booking.get("bookingStatus") not in {"pending", "approved"} or booking.get("paymentStatus") != "unpaid":
            return False
        deadline = online_deadline(booking)
        if deadline is None or deadline > now:
            return False
        session_ref = None
        if booking.get("paymentSessionId"):
            session_ref = db.collection("payment_sessions").document(booking["paymentSessionId"])
            session = session_ref.get(transaction=tx).to_dict()
            if not session or session.get("bookingId") != booking_id or session.get("status") == "paid":
                return False
            if session["expiresAt"] > now:
                return False
        day_ref = db.collection("pitches").document(booking["pitchId"]).collection("availability").document(booking["bookingDate"])
        day = day_ref.get(transaction=tx).to_dict() or {}
        slots = dict(day.get("slots", {}))
        slot = slots.get(booking["slotId"], {})
        if slot.get("bookingId") != booking_id or slot.get("status") not in {"pending", "booked"}:
            return False
        slots[booking["slotId"]] = {"status": "available", "bookingId": None}
        tx.update(day_ref, {"slots": slots, "lastSlotId": booking["slotId"]})
        tx.update(ref, {"bookingStatus": "cancelled", "expiryReason": "online_payment_timeout",
                        "updatedAt": firestore.SERVER_TIMESTAMP})
        if session_ref:
            tx.update(session_ref, {"holdReleasedAt": firestore.SERVER_TIMESTAMP})
        return True
    return expire(db.transaction())


def run_once():
    db = get_db()
    count = 0
    for method in ("vnpay", "payos"):
        for snapshot in db.collection("bookings").where(filter=FieldFilter("paymentMethod", "==", method)).stream():
            if expire_online_booking(snapshot.id):
                count += 1
    return count
