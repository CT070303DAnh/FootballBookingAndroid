import hashlib
import hmac
import unittest
from unittest.mock import patch

from fastapi.testclient import TestClient
from main import app
from services.payment_service import Settings, PaymentError, amount_vnd, canonical_query, sign, verified_callback

CONFIG = Settings("TESTTMN", "test-only-secret-not-a-merchant-key",
                  "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html", "https://example.test/payments/vnpay/return")


def signed_params(**extra):
    params = {"vnp_TmnCode": CONFIG.tmn_code, "vnp_TxnRef": "a" * 32, "vnp_Amount": "20000000",
              "vnp_ResponseCode": "00", "vnp_TransactionStatus": "00", "vnp_TransactionNo": "1000001",
              "vnp_PayDate": "20261009120000", **extra}
    return {**params, "vnp_SecureHash": sign(params, CONFIG.secret)}


class PaymentSignatureTests(unittest.TestCase):
    def test_signs_form_encoded_utf8_not_unescaped_query(self):
        params = {"vnp_OrderInfo": "Sân A + B", "vnp_Amount": "10000"}
        expected = "vnp_Amount=10000&vnp_OrderInfo=S%C3%A2n+A+%2B+B"
        self.assertEqual(canonical_query(params), expected)
        self.assertEqual(sign(params, "key"), hmac.new(b"key", expected.encode(), hashlib.sha512).hexdigest())

    def test_valid_signature_is_accepted(self):
        self.assertEqual(verified_callback(signed_params().items(), CONFIG)["vnp_TxnRef"], "a" * 32)

    def test_tampering_duplicate_keys_wrong_merchant_and_currency_are_rejected(self):
        good = signed_params()
        cases = [{**good, "vnp_Amount": "1"}.items(), list(good.items()) + [("vnp_Amount", "20000000")],
                 signed_params(vnp_TmnCode="OTHER").items(), signed_params(vnp_CurrCode="USD").items(),
                 {**good, "vnp_SecureHash": "00"}.items()]
        for case in cases:
            with self.assertRaises(PaymentError):
                verified_callback(case, CONFIG)

    def test_amount_requires_positive_integral_finite_vnd(self):
        self.assertEqual(amount_vnd(200000.0), 200000)
        for amount in [0, -1, 1.5, float("nan"), float("inf"), None, "200,000"]:
            with self.assertRaises(PaymentError):
                amount_vnd(amount)

    def test_missing_server_config_fails_closed(self):
        with patch.dict("os.environ", {"VNPAY_TMN_CODE":"", "VNPAY_HASH_SECRET":""}):
            with self.assertRaises(PaymentError) as error:
                Settings.load()
        self.assertEqual(error.exception.status, 503)


class PaymentApiTests(unittest.TestCase):
    def setUp(self):
        self.client = TestClient(app)

    def test_sessions_require_authentication(self):
        self.assertEqual(self.client.post("/payments/vnpay/sessions", json={"bookingId":"b"}).status_code,401)
        self.assertEqual(self.client.get("/payments/vnpay/sessions/" + "a"*32).status_code,401)
        self.assertEqual(self.client.post("/payments/vnpay/sessions/" + "a"*32 + "/reconcile").status_code,401)

    @patch("api.payments.verify_firebase_token", return_value={"uid":"verified-customer"})
    @patch("api.payments.reconcile_session", return_value={"status":"pending"})
    def test_reconciliation_uses_verified_uid(self, reconcile, _verify):
        result = self.client.post("/payments/vnpay/sessions/" + "a"*32 + "/reconcile",
                                  headers={"Authorization":"Bearer token"})
        self.assertEqual(result.status_code, 200)
        reconcile.assert_called_once_with("verified-customer", "a"*32)

    @patch("api.payments.verify_firebase_token", return_value={"uid":"verified-customer"})
    @patch("api.payments.create_session", return_value={"status":"pending"})
    def test_uid_comes_from_verified_token_and_client_amount_is_rejected(self, create, verify):
        headers = {"Authorization":"Bearer test-token"}
        result = self.client.post("/payments/vnpay/sessions",headers=headers,json={"bookingId":"b"})
        self.assertEqual(result.status_code,200)
        self.assertEqual(create.call_args.args[:2],("verified-customer","b"))
        verify.assert_called_with("test-token",check_revoked=True)
        for injected in [{"amount":1},{"customerId":"other"},{"returnUrl":"https://evil.test"}]:
            result = self.client.post("/payments/vnpay/sessions",headers=headers,json={"bookingId":"b",**injected})
            self.assertEqual(result.status_code,422)
        self.assertEqual(create.call_count,1)

    @patch("api.payments.Settings.load",return_value=CONFIG)
    @patch("api.payments.reconcile_verified_return")
    @patch("api.payments.receive_ipn")
    def test_browser_return_queries_instead_of_settling_browser_fields(self, settle, query, _settings):
        response = self.client.get("/payments/vnpay/return",params=signed_params(),follow_redirects=False)
        self.assertEqual(response.status_code,303)
        self.assertEqual(response.headers["location"],"vnpay://payment_result?payment_ref=" + "a"*32)
        settle.assert_not_called()
        self.assertEqual(query.call_args.args[0]["vnp_TxnRef"], "a"*32)

    @patch("api.payments.Settings.load",return_value=CONFIG)
    @patch("api.payments.reconcile_verified_return", side_effect=PaymentError("Unavailable", 503))
    def test_return_query_timeout_still_navigates_without_claiming_success(self, query, _settings):
        response = self.client.get("/payments/vnpay/return", params=signed_params(), follow_redirects=False)
        self.assertEqual(response.status_code, 303)
        self.assertNotIn("success", response.headers["location"])

    @patch("api.payments.Settings.load",return_value=CONFIG)
    @patch("api.payments.reconcile_verified_return")
    def test_forged_browser_return_is_rejected(self, query, _settings):
        self.assertEqual(self.client.get("/payments/vnpay/return",params={"vnp_ResponseCode":"00","vnp_TxnRef":"a"*32}).status_code,400)
        query.assert_not_called()

    @patch("api.payments.receive_ipn",side_effect=RuntimeError("sensitive internal details"))
    def test_database_failure_requests_ipn_retry_without_leaking_error(self, _ipn):
        result = self.client.get("/payments/vnpay/ipn")
        self.assertEqual(result.json(),{"RspCode":"99","Message":"Retry later"})

    @patch("api.payments.verify_firebase_token",return_value={"uid":"u"})
    @patch("api.payments.payment_status",side_effect=PaymentError("Not found",404))
    def test_unknown_or_foreign_reference_is_not_success(self, _status, _token):
        result = self.client.get("/payments/vnpay/sessions/" + "a"*32,headers={"Authorization":"Bearer token"})
        self.assertEqual(result.status_code,404)


if __name__ == "__main__":
    unittest.main()
