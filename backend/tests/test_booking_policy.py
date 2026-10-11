import unittest
from unittest.mock import patch, Mock

from services.firestore_service import create_booking_transactionally
from tools.prepare_booking_policy import build_plan


class BookingPolicyTests(unittest.TestCase):
    def setUp(self):
        self.pitch = {"ownerId": "owner", "basePrice": 200000}
        self.booking = {"bookingStatus": "pending", "bookingDate": "2099-01-01", "slotId": "slot_1",
                        "pitchOwnerId": "owner", "startTime": "06:00", "endTime": "07:30"}

    def test_missing_lock_is_rebuilt(self):
        plan = build_plan("p", self.pitch, {}, {"b": self.booking}, {})
        self.assertEqual(plan["2099-01-01"]["slots"]["slot_1"], {"bookingId": "b", "status": "pending"})

    def test_duplicate_live_booking_stops_migration(self):
        with self.assertRaisesRegex(ValueError, "multiple live"):
            build_plan("p", self.pitch, {}, {"b": self.booking, "b2": self.booking}, {})

    def test_unknown_state_requires_manual_review(self):
        with self.assertRaisesRegex(ValueError, "unknown booking state"):
            build_plan("p", self.pitch, {}, {"b": {**self.booking, "bookingStatus": "reserved"}}, {})

    def test_orphan_lock_is_not_silently_released(self):
        with self.assertRaisesRegex(ValueError, "orphan/manual"):
            build_plan("p", self.pitch, {}, {}, {"2099-01-01": {"slots": {"slot_1": {"status": "reserved"}}}})

    def test_rejected_booking_stale_lock_is_released(self):
        b = {**self.booking, "bookingStatus": "rejected"}
        plan = build_plan("p", self.pitch, {}, {"b": b}, {"2099-01-01": {"slots": {"slot_1": {"status": "pending", "bookingId": "b"}}}})
        self.assertEqual(plan["2099-01-01"]["slots"], {})

    def test_overlapping_custom_slots_stop_migration(self):
        slots = {"a": {"isActive": True, "startTime": "06:00", "endTime": "08:00"},
                 "b": {"isActive": True, "startTime": "07:00", "endTime": "09:00"}}
        with self.assertRaisesRegex(ValueError, "overlapping"):
            build_plan("p", self.pitch, slots, {}, {})

    def test_changed_schedule_with_live_booking_stops_migration(self):
        with self.assertRaisesRegex(ValueError, "does not match"):
            build_plan("p", self.pitch, {"other": {"isActive": True, "startTime": "08:00", "endTime": "09:00"}}, {"b": self.booking}, {})


class Snapshot:
    def __init__(self, path, data):
        self.id = path.rsplit("/", 1)[-1]
        self.exists = data is not None
        self.data = data

    def to_dict(self):
        return self.data


class Ref:
    def __init__(self, db, path):
        self.db, self.path = db, path
        self.id = path.rsplit("/", 1)[-1]

    def collection(self, name):
        return Ref(self.db, self.path + "/" + name)

    def document(self, name="new-booking"):
        return Ref(self.db, self.path + "/" + name)

    def get(self, transaction=None):
        self.db.reads.append((self.path, transaction))
        return Snapshot(self.path, self.db.docs.get(self.path))

    def where(self, **_kwargs):
        return self


class FakeDb:
    def __init__(self):
        self.reads = []
        self.docs = {
            "users/customer": {"role": "customer", "status": "active"},
            "pitches/p": {"ownerId": "owner", "status": "available", "basePrice": 200000,
                          "bookingPolicyVersion": 2, "useDefaultSlots": True},
        }
        self.tx = Mock()
        # Python SDK query reads are iterators, unlike reference.get(transaction=tx).
        self.tx.get.side_effect = lambda _query: iter([])

    def collection(self, name):
        return Ref(self, name)

    def transaction(self):
        return self.tx


class BackendTransactionTests(unittest.TestCase):
    def setUp(self):
        self.db = FakeDb()
        self.pending = {"pitchId": "p", "slotId": "slot_1", "date": "2099-01-01",
                        "startTime": "06:00", "endTime": "07:30", "services": [], "totalAmount": 200000}

    def create(self):
        with patch("services.firestore_service.get_db", return_value=self.db), \
                patch("services.firestore_service.firestore.transactional", side_effect=lambda f: f):
            return create_booking_transactionally("customer", self.pending)

    def test_reference_reads_use_transaction_and_lock_is_written(self):
        result = self.create()
        self.assertEqual(result["bookingStatus"], "pending")
        self.assertTrue(all(tx is self.db.tx for _, tx in self.db.reads))
        writes = self.db.tx.set.call_args_list
        availability = next(c.args[1] for c in writes if "/availability/" in c.args[0].path)
        self.assertEqual(availability["lastSlotId"], "slot_1")

    def test_blocked_user_stops_before_any_write(self):
        self.db.docs["users/customer"]["status"] = "blocked"
        with self.assertRaises(PermissionError):
            self.create()
        self.db.tx.create.assert_not_called()

    def test_occupied_lock_stops_before_any_write(self):
        self.db.docs["pitches/p/availability/2099-01-01"] = {"slots": {"slot_1": {"status": "pending", "bookingId": "other"}}}
        with self.assertRaisesRegex(ValueError, "người khác"):
            self.create()
        self.db.tx.create.assert_not_called()

    def test_unmigrated_pitch_stops_before_any_write(self):
        del self.db.docs["pitches/p"]["bookingPolicyVersion"]
        with self.assertRaisesRegex(ValueError, "kiểm tra"):
            self.create()
        self.db.tx.create.assert_not_called()

    def test_price_change_and_nan_are_rejected(self):
        for amount in [1, float("nan")]:
            self.pending["totalAmount"] = amount
            with self.assertRaises(ValueError):
                self.create()
        self.db.tx.create.assert_not_called()


if __name__ == "__main__":
    unittest.main()
