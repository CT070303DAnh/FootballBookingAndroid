"""Invoke every minute from an OS/cloud scheduler; credentials stay on the server.

Explicit --apply required. Default prints counts only. --reconcile is kept for
legacy VNPay sessions; PayOS sessions are settled by webhook.
"""
import argparse
from datetime import datetime, timezone
from pathlib import Path
from dotenv import load_dotenv
import firebase_admin
from services.firestore_service import init_firebase
from services.payment_service import get_db
from services.payment_maintenance import online_deadline, run_once
from services.payment_reconciliation import reconcile_session
from services.payment_service import PaymentError


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apply", action="store_true")
    parser.add_argument("--project", required=True, help="Must match the server credential's project")
    parser.add_argument("--reconcile", action="store_true", help="Query unresolved sessions before expiration; requires --apply")
    args = parser.parse_args()
    load_dotenv(Path(__file__).resolve().parents[1] / ".env")
    init_firebase()
    if firebase_admin.get_app().project_id != args.project:
        parser.error("Configured Firebase project does not match --project")
    if args.reconcile and not args.apply:
        parser.error("--reconcile requires --apply")
    if args.apply:
        if args.reconcile:
            unresolved = 0
            for doc in get_db().collection("payment_sessions").stream():
                session = doc.to_dict()
                if session.get("status") not in {"pending", "failed"}:
                    continue
                if session.get("provider") == "payos":
                    continue
                try:
                    reconcile_session(session["customerId"], doc.id)
                except PaymentError:
                    unresolved += 1
            print("Sessions needing another reconciliation:", unresolved)
        print("Released online holds:", run_once())
    else:
        now = datetime.now(timezone.utc)
        count = 0
        for doc in get_db().collection("bookings").stream():
            b = doc.to_dict()
            deadline = online_deadline(b)
            if deadline and deadline <= now and b.get("paymentStatus") == "unpaid" and b.get("bookingStatus") in {"pending", "approved"}:
                count += 1
        print("Dry run; potentially expired online bookings:", count)


if __name__ == "__main__":
    main()
