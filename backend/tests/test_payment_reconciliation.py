import hashlib
import hmac
import unittest
from datetime import datetime, timezone
from unittest.mock import Mock, patch
import httpx
from services.payment_service import PaymentError, Settings
from services.payment_reconciliation import query_gateway, query_signature, RESPONSE_FIELDS

CONFIG = Settings("TESTTMN", "test-key", "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html",
                  "https://example.test/payments/vnpay/return")


class QueryTests(unittest.TestCase):
    def setUp(self):
        self.session = {"paymentRef": "a"*32, "amountVnd": 200000,
                        "createdAt": datetime(2026, 10, 10, tzinfo=timezone.utc)}
        self.response = {"vnp_ResponseId": "r1", "vnp_Command": "querydr", "vnp_ResponseCode": "00",
                         "vnp_Message": "OK", "vnp_TmnCode": "TESTTMN", "vnp_TxnRef": "a"*32,
                         "vnp_Amount": "20000000", "vnp_BankCode": "NCB", "vnp_PayDate": "20261010070100",
                         "vnp_TransactionNo": "123", "vnp_TransactionType": "01", "vnp_TransactionStatus": "00",
                         "vnp_OrderInfo": "test"}

    def call(self, data=None):
        data = dict(self.response if data is None else data)
        data.setdefault("vnp_SecureHash", query_signature(data, RESPONSE_FIELDS, CONFIG.secret))
        with patch.dict("os.environ", {"VNPAY_SERVER_IP": "127.0.0.1"}), patch(
                "services.payment_reconciliation.httpx.post", return_value=Mock(json=lambda: data)) as post:
            result = query_gateway(self.session, CONFIG)
            payload = post.call_args.kwargs["json"]
            # Independent expected canonical request: pipe-separated, NOT URL encoded.
            raw = "|".join([payload["vnp_RequestId"], "2.1.0", "querydr", "TESTTMN", "a"*32,
                            "20261010070000", payload["vnp_CreateDate"], "127.0.0.1", "Kiem tra " + "a"*32])
            self.assertEqual(payload["vnp_SecureHash"], hmac.new(b"test-key", raw.encode(), hashlib.sha512).hexdigest())
            self.assertFalse(post.call_args.kwargs["follow_redirects"])
            return result

    def test_signed_query_response(self):
        self.assertEqual(self.call()["vnp_TransactionStatus"], "00")

    def test_tampered_signature(self):
        with self.assertRaises(PaymentError):
            self.call({**self.response, "vnp_SecureHash": "0"*128})

    def test_wrong_amount_reference_type_or_merchant(self):
        for field, value in [("vnp_Amount", "100"), ("vnp_TxnRef", "b"*32),
                             ("vnp_TransactionType", "02"), ("vnp_TmnCode", "OTHER")]:
            with self.subTest(field=field), self.assertRaises(PaymentError):
                self.call({**self.response, field: value})

    def test_not_found_and_throttled_are_not_payment_failure(self):
        for code in ["91", "94", "99"]:
            with self.assertRaises(PaymentError):
                self.call({**self.response, "vnp_ResponseCode": code})

    def test_timeout_never_returns_a_failed_payment(self):
        with patch.dict("os.environ", {"VNPAY_SERVER_IP": "127.0.0.1"}), patch(
                "services.payment_reconciliation.httpx.post", side_effect=httpx.ReadTimeout("timeout")):
            with self.assertRaises(PaymentError) as error:
                query_gateway(self.session, CONFIG)
            self.assertEqual(error.exception.status, 503)

    def test_non_string_and_separator_fields_rejected(self):
        for value in [123, None, "OK|forged"]:
            with self.assertRaises(PaymentError):
                query_signature({**self.response, "vnp_Message": value}, RESPONSE_FIELDS, CONFIG.secret)
