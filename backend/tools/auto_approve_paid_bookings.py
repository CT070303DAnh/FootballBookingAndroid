"""Approve legacy PayOS bookings that were paid before auto-approval was enabled.

Default mode is a read-only dry run. Use --apply only after reviewing the
candidate count. The script only changes PayOS bookings that have a paid
payment session and still own their availability lock.
"""

import argparse
from pathlib import Path

from dotenv import load_dotenv
from firebase_admin import firestore
import firebase_admin
from google.cloud.firestore_v1.base_query import FieldFilter

from services.firestore_service import get_db, init_firebase


def approve_one(db, snapshot, apply: bool) -> str:
    ref = snapshot.reference

    @firestore.transactional
    def transition(tx):
        booking = ref.get(transaction=tx).to_dict() or {}
        if (booking.get("paymentMethod") != "payos"
                or booking.get("paymentStatus") != "paid"
                or booking.get("bookingStatus") != "pending"):
            return "skipped"

        session_id = booking.get("paymentSessionId")
        transaction_id = booking.get("payosTransactionId")
        if not session_id or not transaction_id:
            return "skipped_missing_payment_evidence"

        session_ref = db.collection("payment_sessions").document(session_id)
        session = session_ref.get(transaction=tx).to_dict() or {}
        if (session.get("status") != "paid"
                or session.get("bookingId") != ref.id
                or session.get("payosTransactionId") != transaction_id):
            return "skipped_payment_session_mismatch"

        day_ref = (db.collection("pitches").document(booking["pitchId"])
                   .collection("availability").document(booking["bookingDate"]))
        day = day_ref.get(transaction=tx).to_dict() or {}
        lock = (day.get("slots", {}) or {}).get(booking["slotId"], {})
        if (lock.get("bookingId") != ref.id
                or lock.get("status") not in {"pending", "booked"}):
            return "skipped_slot_mismatch"

        if not apply:
            return "would_approve"

        tx.update(ref, {
            "bookingStatus": "approved",
            "paymentReviewRequired": False,
            "paymentApprovedAt": session.get("paidAt"),
            "approvalSource": "payos_migration",
            "updatedAt": firestore.SERVER_TIMESTAMP,
        })
        tx.update(day_ref, {
            f"slots.{booking['slotId']}": {
                "status": "booked",
                "bookingId": ref.id,
            },
            "lastSlotId": booking["slotId"],
        })
        return "approved"

    return transition(db.transaction())


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apply", action="store_true")
    parser.add_argument("--project", required=True)
    args = parser.parse_args()

    load_dotenv(Path(__file__).resolve().parents[1] / ".env")
    init_firebase()
    if firebase_admin.get_app().project_id != args.project:
        parser.error("Configured Firebase project does not match --project")

    db = get_db()
    query = (db.collection("bookings")
             .where(filter=FieldFilter("paymentMethod", "==", "payos"))
             .where(filter=FieldFilter("paymentStatus", "==", "paid"))
             .where(filter=FieldFilter("bookingStatus", "==", "pending")))

    counts = {}
    for snapshot in query.stream():
        result = approve_one(db, snapshot, args.apply)
        counts[result] = counts.get(result, 0) + 1

    mode = "Applied" if args.apply else "Dry run"
    print(mode + ":", counts or "no paid pending PayOS bookings")


if __name__ == "__main__":
    main()
