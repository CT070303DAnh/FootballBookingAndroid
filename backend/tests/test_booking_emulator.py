"""Real Python Firestore SDK coverage. Only runs against the explicit local emulator."""
import os
import unittest
from concurrent.futures import ThreadPoolExecutor
from threading import Barrier
from unittest.mock import patch

from google.auth.credentials import AnonymousCredentials
from google.cloud.firestore import Client
from google.cloud.firestore_v1.base_query import FieldFilter
from services.firestore_service import create_booking_transactionally, get_slots


@unittest.skipUnless(os.environ.get("FIRESTORE_EMULATOR_HOST") == "127.0.0.1:8089", "local emulator required")
class BookingEmulatorTests(unittest.TestCase):
    def setUp(self):
        self.db = Client(project="demo-football-booking", credentials=AnonymousCredentials())
        self.pitch = self.db.collection("pitches").document("backend-c2")
        for snapshot in self.db.collection("bookings").where(filter=FieldFilter("pitchId", "==", self.pitch.id)).stream():
            snapshot.reference.delete()
        for snapshot in self.pitch.collection("availability").stream():
            snapshot.reference.delete()
        self.pitch.set({"ownerId":"backend-owner", "status":"available", "basePrice":200000,
                        "name":"Pitch", "address":"Address", "type":"5",
                        "bookingPolicyVersion":2, "useDefaultSlots":True})
        self.db.collection("users").document("backend-customer").set({"role":"customer", "status":"active"})
        self.pending = {"pitchId":self.pitch.id, "slotId":"slot_1", "date":"2099-01-01",
                        "startTime":"06:00", "endTime":"07:30", "services":[], "totalAmount":200000}

    def tearDown(self):
        self.db.close()

    def test_real_sdk_creates_booking_and_lock(self):
        with patch("services.firestore_service.get_db", return_value=self.db):
            result = create_booking_transactionally("backend-customer", self.pending)
        saved = self.db.collection("bookings").document(result["bookingId"]).get().to_dict()
        lock = self.pitch.collection("availability").document("2099-01-01").get().to_dict()
        self.assertEqual(saved["totalAmount"], 200000)
        self.assertEqual(lock["slots"]["slot_1"]["bookingId"], result["bookingId"])

    def test_confirmation_retry_returns_same_booking_after_commit(self):
        self.pending["confirmationId"] = "a" * 32
        with patch("services.firestore_service.get_db", return_value=self.db):
            first = create_booking_transactionally("backend-customer", self.pending)
            second = create_booking_transactionally("backend-customer", self.pending)
        self.assertEqual(first["bookingId"], second["bookingId"])
        docs = list(self.db.collection("bookings").where(filter=FieldFilter("pitchId", "==", self.pitch.id)).stream())
        self.assertEqual(len(docs), 1)

    def test_slots_respect_policy_defaults_and_unknown_lock(self):
        with patch("services.firestore_service.get_db", return_value=self.db):
            self.assertTrue(get_slots(self.pitch.id, "2099-01-01")[0]["available"])
            self.pitch.update({"bookingPolicyVersion": 1})
            self.assertFalse(any(s["available"] for s in get_slots(self.pitch.id, "2099-01-01")))
            self.pitch.update({"bookingPolicyVersion": 2})
            self.pitch.collection("availability").document("2099-01-01").set({"slots": {"slot_1": {"status": "maintenance"}}})
            self.assertFalse(get_slots(self.pitch.id, "2099-01-01")[0]["available"])
            self.pitch.update({"useDefaultSlots": False})
            self.assertEqual(get_slots(self.pitch.id, "2099-01-01"), [])

    def test_two_backend_transactions_have_one_winner(self):
        barrier = Barrier(2)
        def attempt():
            barrier.wait(timeout=10)
            try:
                return create_booking_transactionally("backend-customer", self.pending)
            except ValueError:
                return None
        with patch("services.firestore_service.get_db", return_value=self.db), ThreadPoolExecutor(max_workers=2) as executor:
            results = list(executor.map(lambda _: attempt(), range(2)))
        self.assertEqual(sum(r is not None for r in results), 1)
        bookings = list(self.db.collection("bookings").where(filter=FieldFilter("pitchId", "==", self.pitch.id)).stream())
        self.assertEqual(len(bookings), 1)


if __name__ == "__main__":
    unittest.main()
