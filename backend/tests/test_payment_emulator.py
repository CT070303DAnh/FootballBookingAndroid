import os
import unittest
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timedelta, timezone
from threading import Barrier
from unittest.mock import patch
from urllib.parse import parse_qsl, urlsplit

from google.auth.credentials import AnonymousCredentials
from google.cloud.firestore import Client
from google.cloud.firestore_v1.base_query import FieldFilter
from services.payment_service import Settings, PaymentError, VIETNAM, create_session, payment_status, receive_ipn, sign
from services.payment_maintenance import expire_online_booking
from services.payment_reconciliation import reconcile_session, reconcile_verified_return

CONFIG = Settings("TESTTMN", "local-test-key", "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html",
                  "https://example.test/payments/vnpay/return")


@unittest.skipUnless(os.environ.get("FIRESTORE_EMULATOR_HOST") == "127.0.0.1:8089", "local emulator required")
class PaymentEmulatorTests(unittest.TestCase):
    def setUp(self):
        self.db = Client(project="demo-football-booking",credentials=AnonymousCredentials())
        for doc in self.db.collection("payment_sessions").where(filter=FieldFilter("customerId","==","pay-customer")).stream():
            doc.reference.delete()
        for number in ["1000001","1000002"]:
            self.db.collection("vnpay_transactions").document(number).delete()
        self.db.collection("users").document("pay-customer").set({"role":"customer","status":"active"})
        self.booking = self.db.collection("bookings").document("pay-c3")
        self.booking.set({"customerId":"pay-customer","bookingStatus":"pending","paymentStatus":"unpaid",
                          "totalAmount":200000,"bookingDate":"2099-01-01","startTime":"06:00","paymentMethod":"cash",
                          "pitchId":"pay-pitch", "slotId":"slot_1"})
        self.db.document("pitches/pay-pitch/availability/2099-01-01").set({
            "date":"2099-01-01", "slots":{"slot_1":{"status":"pending", "bookingId":"pay-c3"}}})
        self.db_patch = patch("services.payment_service.get_db",return_value=self.db)
        self.config_patch = patch("services.payment_service.Settings.load",return_value=CONFIG)
        self.db_patch.start()
        self.config_patch.start()

    def tearDown(self):
        self.db_patch.stop()
        self.config_patch.stop()
        self.db.close()

    def create(self):
        return create_session("pay-customer","pay-c3","127.0.0.1")

    def prepare_hold(self, method="vnpay"):
        self.booking.update({"paymentMethod": method, "pitchId": "pay-pitch", "slotId": "slot_1",
                             "createdAt": datetime.now(timezone.utc)-timedelta(minutes=20)})
        day = self.db.document("pitches/pay-pitch/availability/2099-01-01")
        day.set({"date": "2099-01-01", "slots": {"slot_1": {"status": "pending", "bookingId": "pay-c3"},
                                                     "slot_2": {"status": "booked", "bookingId": "other"}}})
        return day

    def expire(self):
        with patch("services.payment_maintenance.get_db", return_value=self.db):
            return expire_online_booking("pay-c3")

    def test_expire_online_hold_only_once_and_preserve_other_slot(self):
        day = self.prepare_hold()
        self.assertTrue(self.expire())
        self.assertFalse(self.expire())
        self.assertEqual(day.get().to_dict()["slots"]["slot_1"]["status"], "available")
        self.assertEqual(day.get().to_dict()["slots"]["slot_2"]["bookingId"], "other")
        self.assertEqual(self.booking.get().to_dict()["bookingStatus"], "cancelled")

    def test_cash_pay_later_paid_and_foreign_lock_never_expire(self):
        for method in ["cash", "pay_later"]:
            self.prepare_hold(method)
            self.assertFalse(self.expire())
        day = self.prepare_hold()
        self.booking.update({"paymentStatus": "paid"})
        self.assertFalse(self.expire())
        self.booking.update({"paymentStatus": "unpaid"})
        day.update({"slots.slot_1.bookingId": "another-booking"})
        self.assertFalse(self.expire())

    def test_payment_after_release_requires_review_without_reclaiming_slot(self):
        session = self.create()
        day = self.prepare_hold()
        ref = self.db.collection("payment_sessions").document(session["paymentRef"])
        ref.update({"expiresAt": datetime.now(timezone.utc)-timedelta(seconds=1)})
        self.assertTrue(self.expire())
        day.update({"slots.slot_1": {"status": "pending", "bookingId": "new-customer"}})
        self.assertEqual(receive_ipn(self.callback(session))["RspCode"], "00")
        self.assertEqual(payment_status("pay-customer", session["paymentRef"])["status"], "review")
        self.assertEqual(day.get().to_dict()["slots"]["slot_1"]["bookingId"], "new-customer")

    def test_paid_ipn_before_expiration_prevents_release(self):
        session = self.create()
        self.prepare_hold()
        receive_ipn(self.callback(session))
        self.assertFalse(self.expire())

    def test_query_recovers_missing_ipn_and_is_rate_limited(self):
        session = self.create()
        ref = self.db.collection("payment_sessions").document(session["paymentRef"])
        ref.update({"createdAt": datetime.now(timezone.utc)-timedelta(minutes=2)})
        data = dict(self.callback(session))
        with patch("services.payment_reconciliation.get_db", return_value=self.db), patch(
                "services.payment_reconciliation.query_gateway", return_value=data) as query:
            result = reconcile_session("pay-customer", session["paymentRef"])
            self.assertEqual(result["status"], "paid")
            self.assertEqual(reconcile_session("pay-customer", session["paymentRef"])["status"], "paid")
            self.assertEqual(query.call_count, 1)

    def test_query_pending_and_errors_do_not_mark_failed(self):
        session = self.create()
        ref = self.db.collection("payment_sessions").document(session["paymentRef"])
        ref.update({"createdAt": datetime.now(timezone.utc)-timedelta(minutes=2)})
        with patch("services.payment_reconciliation.get_db", return_value=self.db), patch(
                "services.payment_reconciliation.query_gateway", return_value={"vnp_TransactionStatus": "01"}) as query:
            self.assertEqual(reconcile_session("pay-customer", session["paymentRef"])["status"], "pending")
            reconcile_session("pay-customer", session["paymentRef"])
            self.assertEqual(query.call_count, 1)
        self.assertEqual(self.booking.get().to_dict()["paymentStatus"], "unpaid")

    def test_online_deadline_cannot_be_extended_by_opening_payment_late(self):
        self.prepare_hold()
        with self.assertRaises(PaymentError):
            self.create()

    def test_signed_return_success_does_not_override_pending_query(self):
        session = self.create()
        ref = self.db.collection("payment_sessions").document(session["paymentRef"])
        ref.update({"createdAt": datetime.now(timezone.utc)-timedelta(minutes=2)})
        with patch("services.payment_reconciliation.get_db", return_value=self.db), patch(
                "services.payment_reconciliation.query_gateway", return_value={"vnp_TransactionStatus": "01"}) as query:
            reconcile_verified_return(dict(self.callback(session)))
            reconcile_verified_return(dict(self.callback(session)))
            self.assertEqual(query.call_count, 1)
        self.assertEqual(self.booking.get().to_dict()["paymentStatus"], "unpaid")

    def test_return_checks_amount_before_query_and_query_can_recover_without_ipn(self):
        session = self.create()
        ref = self.db.collection("payment_sessions").document(session["paymentRef"])
        ref.update({"createdAt": datetime.now(timezone.utc)-timedelta(minutes=2)})
        with patch("services.payment_reconciliation.get_db", return_value=self.db), patch(
                "services.payment_reconciliation.query_gateway", return_value=dict(self.callback(session))) as query:
            with self.assertRaises(PaymentError):
                reconcile_verified_return(dict(self.callback(session, vnp_Amount="100")))
            query.assert_not_called()
            reconcile_verified_return(dict(self.callback(session)))
            reconcile_verified_return(dict(self.callback(session)))
            self.assertEqual(query.call_count, 1)
        self.assertEqual(self.booking.get().to_dict()["paymentStatus"], "paid")

    def test_success_with_missing_lock_is_review_not_booked(self):
        session = self.create()
        self.db.document("pitches/pay-pitch/availability/2099-01-01").delete()
        receive_ipn(self.callback(session))
        self.assertEqual(payment_status("pay-customer", session["paymentRef"])["status"], "review")

    def test_expiration_racing_ipn_never_releases_a_paid_booking_silently(self):
        session = self.create()
        day = self.prepare_hold()
        self.db.collection("payment_sessions").document(session["paymentRef"]).update({
            "expiresAt": datetime.now(timezone.utc)-timedelta(seconds=1)})
        callback = self.callback(session)
        with ThreadPoolExecutor(max_workers=2) as pool:
            expiry = pool.submit(self.expire)
            ipn = pool.submit(receive_ipn, callback)
            expiry.result()
            self.assertEqual(ipn.result()["RspCode"], "00")
        b = self.booking.get().to_dict()
        self.assertEqual(b["paymentStatus"], "paid")
        if b["bookingStatus"] == "cancelled":
            self.assertTrue(b["paymentReviewRequired"])
            self.assertEqual(day.get().to_dict()["slots"]["slot_1"]["status"], "available")
        else:
            self.assertEqual(day.get().to_dict()["slots"]["slot_1"]["bookingId"], "pay-c3")

    def callback(self, session, **extra):
        params = {"vnp_TmnCode":CONFIG.tmn_code,"vnp_TxnRef":session["paymentRef"],"vnp_Amount":"20000000",
                  "vnp_ResponseCode":"00","vnp_TransactionStatus":"00","vnp_TransactionNo":"1000001",
                  "vnp_PayDate":datetime.now(VIETNAM).strftime("%Y%m%d%H%M%S"),**extra}
        return list({**params,"vnp_SecureHash":sign(params,CONFIG.secret)}.items())

    def test_create_reads_price_and_reuses_same_session(self):
        first, second = self.create(), self.create()
        self.assertEqual(first,second)
        params = dict(parse_qsl(urlsplit(first["paymentUrl"]).query))
        self.assertEqual(params["vnp_Amount"],"20000000")
        signature = params.pop("vnp_SecureHash")
        self.assertEqual(signature,sign(params,CONFIG.secret))
        self.assertEqual(payment_status("pay-customer",first["paymentRef"])["status"],"pending")

    def test_concurrent_creation_has_one_reference(self):
        barrier = Barrier(2)
        def create():
            barrier.wait(timeout=10)
            return self.create()
        with ThreadPoolExecutor(max_workers=2) as pool:
            results=list(pool.map(lambda _:create(),range(2)))
        self.assertEqual(results[0]["paymentRef"],results[1]["paymentRef"])

    def test_success_is_committed_before_status_becomes_paid(self):
        session=self.create()
        self.assertEqual(receive_ipn(self.callback(session))["RspCode"],"00")
        self.assertEqual(payment_status("pay-customer",session["paymentRef"])["status"],"paid")
        saved=self.booking.get().to_dict()
        self.assertEqual(saved["vnpayTransactionId"],"1000001")
        self.assertEqual(saved["paymentMethod"],"vnpay")

    def test_duplicate_and_concurrent_ipn_are_idempotent(self):
        session=self.create()
        callback=self.callback(session)
        with ThreadPoolExecutor(max_workers=2) as pool:
            results=list(pool.map(lambda _:receive_ipn(callback),range(2)))
        self.assertEqual(sorted(r["RspCode"] for r in results),["00","02"])
        self.assertEqual(receive_ipn(self.callback(session,vnp_TransactionNo="1000002"))["RspCode"],"99")

    def test_wrong_amount_or_reference_never_marks_paid(self):
        session=self.create()
        self.assertEqual(receive_ipn(self.callback(session,vnp_Amount="100"))["RspCode"],"04")
        self.assertEqual(receive_ipn(self.callback(session,vnp_TxnRef="f"*32))["RspCode"],"01")
        self.assertEqual(self.booking.get().to_dict()["paymentStatus"],"unpaid")

    def test_tampering_and_duplicate_parameters_never_mark_paid(self):
        session=self.create()
        callback=self.callback(session)
        self.assertEqual(receive_ipn(callback+[("vnp_Amount","20000000")])["RspCode"],"97")
        forged=dict(callback)
        forged["vnp_Amount"]="1"
        self.assertEqual(receive_ipn(forged.items())["RspCode"],"97")
        self.assertEqual(self.booking.get().to_dict()["paymentStatus"],"unpaid")

    def test_both_response_and_transaction_status_must_succeed(self):
        session=self.create()
        self.assertEqual(receive_ipn(self.callback(session,vnp_TransactionStatus="02"))["RspCode"],"00")
        self.assertEqual(self.booking.get().to_dict()["paymentStatus"],"unpaid")
        self.assertEqual(payment_status("pay-customer",session["paymentRef"])["status"],"failed")

    def test_failure_after_success_cannot_downgrade_paid(self):
        session=self.create()
        receive_ipn(self.callback(session))
        self.assertEqual(receive_ipn(self.callback(session,vnp_ResponseCode="24",vnp_TransactionStatus="02"))["RspCode"],"02")
        self.assertEqual(self.booking.get().to_dict()["paymentStatus"],"paid")

    def test_cancelled_booking_payment_requires_review_without_reviving_booking(self):
        session=self.create()
        self.booking.update({"bookingStatus":"cancelled"})
        self.assertEqual(receive_ipn(self.callback(session))["RspCode"],"00")
        self.assertEqual(self.booking.get().to_dict()["bookingStatus"],"cancelled")
        self.assertEqual(payment_status("pay-customer",session["paymentRef"])["status"],"review")

    def test_expired_session_is_not_replaced_and_late_payment_requires_review(self):
        session=self.create()
        self.db.collection("payment_sessions").document(session["paymentRef"]).update({"expiresAt":datetime.now(timezone.utc)-timedelta(minutes=1)})
        repeated=self.create()
        self.assertEqual(repeated["paymentRef"],session["paymentRef"])
        self.assertEqual(repeated["status"],"expired")
        self.assertNotIn("paymentUrl",repeated)
        receive_ipn(self.callback(session))
        self.assertEqual(payment_status("pay-customer",session["paymentRef"])["status"],"review")

    def test_foreign_or_blocked_user_cannot_create_or_read(self):
        session=self.create()
        for operation in [lambda:create_session("other","pay-c3","127.0.0.1"),lambda:payment_status("other",session["paymentRef"])]:
            with self.assertRaises(PaymentError): operation()
        self.db.collection("users").document("pay-customer").update({"status":"blocked"})
        for operation in [self.create,lambda:payment_status("pay-customer",session["paymentRef"])]:
            with self.assertRaises(PaymentError): operation()

    def test_same_bank_transaction_cannot_pay_two_bookings(self):
        first=self.create()
        receive_ipn(self.callback(first))
        second_booking=self.db.collection("bookings").document("pay-c3-two")
        second_booking.set({"customerId":"pay-customer","bookingStatus":"pending","paymentStatus":"unpaid",
                            "totalAmount":200000,"bookingDate":"2099-01-01","startTime":"06:00"})
        second=create_session("pay-customer",second_booking.id,"127.0.0.1")
        self.assertEqual(receive_ipn(self.callback(second))["RspCode"],"99")
        self.assertEqual(second_booking.get().to_dict()["paymentStatus"],"unpaid")


if __name__ == "__main__":
    unittest.main()
