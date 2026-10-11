"""VNPay 2.1.0: server-created orders, authenticated status, signed IPN settlement."""
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from decimal import Decimal, InvalidOperation
import hashlib
import hmac
import ipaddress
import os
import re
from urllib.parse import urlencode, urlsplit
import uuid
from zoneinfo import ZoneInfo

from firebase_admin import firestore
from services.firestore_service import get_db

VIETNAM = ZoneInfo("Asia/Ho_Chi_Minh")
ONLINE_HOLD_MINUTES = 15


class PaymentError(Exception):
    def __init__(self, message, status=400):
        super().__init__(message)
        self.status = status


@dataclass(frozen=True)
class Settings:
    tmn_code: str
    secret: str
    gateway: str
    return_url: str

    @classmethod
    def load(cls):
        config = cls(os.getenv("VNPAY_TMN_CODE", ""), os.getenv("VNPAY_HASH_SECRET", ""),
                     os.getenv("VNPAY_GATEWAY_URL", "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html"),
                     os.getenv("VNPAY_RETURN_URL", ""))
        callback = urlsplit(config.return_url)
        if (not config.tmn_code or not config.secret or config.tmn_code == "YOUR_TMN_CODE"
                or config.gateway not in {"https://sandbox.vnpayment.vn/paymentv2/vpcpay.html", "https://pay.vnpay.vn/vpcpay.html"}
                or callback.scheme != "https" or not callback.hostname or callback.username or callback.password
                or callback.query or callback.fragment or callback.path != "/payments/vnpay/return"):
            raise PaymentError("Thanh toán chưa được cấu hình. Vui lòng thử lại sau.", 503)
        return config


def canonical_query(params):
    # Sign exactly the same UTF-8, form-encoded bytes that go into the URL.
    return urlencode(sorted((k, v) for k, v in params.items() if v != ""))


def sign(params, secret):
    return hmac.new(secret.encode("utf-8"), canonical_query(params).encode("utf-8"), hashlib.sha512).hexdigest()


def verified_callback(items, config):
    params = {}
    for key, value in items:
        if key in params or not key.startswith("vnp_"):
            raise PaymentError("Callback không hợp lệ")
        params[key] = value
    signature = params.pop("vnp_SecureHash", "")
    algorithm = params.pop("vnp_SecureHashType", "SHA512")
    if algorithm.upper() != "SHA512" or not re.fullmatch(r"[0-9a-fA-F]{128}", signature):
        raise PaymentError("Chữ ký không hợp lệ")
    if not hmac.compare_digest(sign(params, config.secret), signature.lower()):
        raise PaymentError("Chữ ký không hợp lệ")
    if params.get("vnp_TmnCode") != config.tmn_code or not re.fullmatch(r"[a-f0-9]{32}", params.get("vnp_TxnRef", "")):
        raise PaymentError("Mã giao dịch không hợp lệ")
    if params.get("vnp_CurrCode", "VND") != "VND":
        raise PaymentError("Loại tiền không hợp lệ")
    return params


def amount_vnd(value):
    try:
        amount = Decimal(str(value))
        if not amount.is_finite() or amount <= 0 or amount != amount.to_integral_value() or amount * 100 > 999999999999:
            raise ValueError()
        return int(amount)
    except (InvalidOperation, ValueError, TypeError):
        raise PaymentError("Số tiền đơn đặt sân không hợp lệ") from None


def authorize(uid, user, booking):
    if not user or user.get("role") != "customer" or user.get("status", "active") != "active":
        raise PaymentError("Tài khoản không được phép thanh toán", 403)
    if not booking or booking.get("customerId") != uid:
        raise PaymentError("Không tìm thấy đơn đặt sân", 404)


def session_result(session, booking, include_url=False):
    # Never infer success from a browser return or merely from booking.paymentStatus.
    status = session["status"]
    if status == "paid" and (booking.get("paymentReviewRequired") or booking.get("bookingStatus") not in {"pending", "approved"}):
        status = "review"
    elif status == "paid" and (booking.get("paymentStatus") != "paid"
                              or booking.get("vnpayTransactionId") != session.get("transactionNo")):
        status = "review"
    elif status == "pending" and booking.get("paymentStatus") != "unpaid":
        status = "review"
    elif status == "pending" and booking.get("bookingStatus") not in {"pending", "approved"}:
        status = "cancelled"
    elif status == "pending" and session["expiresAt"] <= datetime.now(timezone.utc):
        status = "expired"
    result = {"bookingId": session["bookingId"], "paymentRef": session["paymentRef"],
              "status": status, "amount": session["amountVnd"],
              "expiresAt": session["expiresAt"].isoformat(), "bookingStatus": booking.get("bookingStatus")}
    if include_url and status == "pending":
        result["paymentUrl"] = session["paymentUrl"]
    return result


def create_session(uid, booking_id, client_ip):
    config = Settings.load()
    try:
        ipaddress.ip_address(client_ip)
    except ValueError:
        raise PaymentError("Không xác định được địa chỉ kết nối", 400) from None
    db = get_db()
    booking_ref = db.collection("bookings").document(booking_id)
    user_ref = db.collection("users").document(uid)
    payment_ref = uuid.uuid4().hex
    new_ref = db.collection("payment_sessions").document(payment_ref)

    @firestore.transactional
    def commit(tx):
        user = user_ref.get(transaction=tx).to_dict()
        booking = booking_ref.get(transaction=tx).to_dict()
        authorize(uid, user, booking)
        existing = booking.get("paymentSessionId")
        if existing:
            session = db.collection("payment_sessions").document(existing).get(transaction=tx).to_dict()
            if not session or session.get("bookingId") != booking_id or session.get("customerId") != uid:
                raise PaymentError("Giao dịch cần được đối soát trước khi thanh toán lại", 409)
            return session_result(session, booking, include_url=booking.get("bookingStatus") in {"pending", "approved"})
        if booking.get("bookingStatus") not in {"pending", "approved"} or booking.get("paymentStatus") != "unpaid":
            raise PaymentError("Đơn không còn ở trạng thái có thể thanh toán", 409)
        try:
            starts = datetime.strptime(booking["bookingDate"] + " " + booking["startTime"], "%Y-%m-%d %H:%M").replace(tzinfo=VIETNAM)
        except (KeyError, ValueError, TypeError):
            raise PaymentError("Thời gian đơn không hợp lệ", 409) from None
        now = datetime.now(timezone.utc).replace(microsecond=0)
        if starts <= now:
            raise PaymentError("Đơn đã đến giờ thi đấu", 409)
        expires = min(now + timedelta(minutes=ONLINE_HOLD_MINUTES), starts)
        if booking.get("paymentMethod") == "vnpay" and isinstance(booking.get("createdAt"), datetime):
            expires = min(expires, booking["createdAt"] + timedelta(minutes=ONLINE_HOLD_MINUTES))
        if expires <= now:
            raise PaymentError("Đã hết thời gian giữ chỗ thanh toán online. Hãy kiểm tra lịch sử và chọn lại sân.", 409)
        amount = amount_vnd(booking.get("totalAmount"))
        params = {"vnp_Version": "2.1.0", "vnp_Command": "pay", "vnp_TmnCode": config.tmn_code,
                  "vnp_Amount": str(amount * 100), "vnp_CurrCode": "VND", "vnp_TxnRef": payment_ref,
                  "vnp_OrderInfo": "Thanh toan dat san " + payment_ref, "vnp_OrderType": "other",
                  "vnp_Locale": "vn", "vnp_ReturnUrl": config.return_url, "vnp_IpAddr": client_ip,
                  "vnp_CreateDate": now.astimezone(VIETNAM).strftime("%Y%m%d%H%M%S"),
                  "vnp_ExpireDate": expires.astimezone(VIETNAM).strftime("%Y%m%d%H%M%S")}
        url = config.gateway + "?" + canonical_query(params) + "&vnp_SecureHash=" + sign(params, config.secret)
        session = {"bookingId": booking_id, "customerId": uid, "paymentRef": payment_ref,
                   "amountVnd": amount, "tmnCode": config.tmn_code, "status": "pending",
                   "createdAt": now, "expiresAt": expires, "paymentUrl": url}
        tx.create(new_ref, session)
        tx.update(booking_ref, {"paymentSessionId": payment_ref, "updatedAt": firestore.SERVER_TIMESTAMP})
        return session_result(session, booking, include_url=True)

    return commit(db.transaction())


def payment_status(uid, payment_ref):
    db = get_db()
    # A transaction provides one consistent view across the settlement writes.
    @firestore.transactional
    def read(tx):
        session = db.collection("payment_sessions").document(payment_ref).get(transaction=tx).to_dict()
        if not session or session.get("customerId") != uid:
            raise PaymentError("Không tìm thấy giao dịch", 404)
        user = db.collection("users").document(uid).get(transaction=tx).to_dict()
        booking = db.collection("bookings").document(session["bookingId"]).get(transaction=tx).to_dict()
        authorize(uid, user, booking)
        return session_result(session, booking)
    return read(db.transaction())


def receive_ipn(items):
    config = Settings.load()
    try:
        params = verified_callback(items, config)
    except PaymentError:
        return {"RspCode": "97", "Message": "Invalid signature or parameters"}
    return settle_verified(params, config)


def settle_verified(params, config):
    """Only call after IPN or QueryDR signature verification."""
    amount_text = params.get("vnp_Amount", "")
    response_code, transaction_status = params.get("vnp_ResponseCode", ""), params.get("vnp_TransactionStatus", "")
    if not re.fullmatch(r"[0-9]{1,12}", amount_text) or not re.fullmatch(r"[0-9]{2}", response_code) or not re.fullmatch(r"[0-9]{2}", transaction_status):
        return {"RspCode": "99", "Message": "Invalid parameters"}
    success = response_code == "00" and transaction_status == "00"
    transaction_no = params.get("vnp_TransactionNo", "")
    paid_at = None
    if success:
        try:
            if not re.fullmatch(r"[0-9]{1,64}", transaction_no) or int(transaction_no) == 0:
                raise ValueError()
            if not re.fullmatch(r"[0-9]{14}", params.get("vnp_PayDate", "")):
                raise ValueError()
            paid_at = datetime.strptime(params["vnp_PayDate"], "%Y%m%d%H%M%S").replace(tzinfo=VIETNAM)
        except (ValueError, KeyError):
            return {"RspCode": "99", "Message": "Invalid transaction details"}
    db = get_db()
    ref = db.collection("payment_sessions").document(params["vnp_TxnRef"])

    @firestore.transactional
    def settle(tx):
        session = ref.get(transaction=tx).to_dict()
        if not session or session.get("tmnCode") != config.tmn_code:
            return {"RspCode": "01", "Message": "Order not found"}
        if int(amount_text) != session["amountVnd"] * 100:
            return {"RspCode": "04", "Message": "Invalid amount"}
        booking_ref = db.collection("bookings").document(session["bookingId"])
        booking = booking_ref.get(transaction=tx).to_dict()
        if not booking or booking.get("paymentSessionId") != ref.id or booking.get("customerId") != session["customerId"]:
            return {"RspCode": "01", "Message": "Order mismatch"}
        if session["status"] == "paid":
            if success and session.get("transactionNo") != transaction_no:
                return {"RspCode": "99", "Message": "Conflicting bank transaction"}
            return {"RspCode": "02", "Message": "Order already confirmed"}
        if not success:
            if session["status"] == "failed":
                return {"RspCode": "02", "Message": "Order already confirmed"}
            tx.update(ref, {"status": "failed", "responseCode": response_code,
                            "transactionStatus": transaction_status, "updatedAt": firestore.SERVER_TIMESTAMP})
            return {"RspCode": "00", "Message": "Confirm success"}
        bank_ref = db.collection("vnpay_transactions").document(transaction_no)
        bank_transaction = bank_ref.get(transaction=tx).to_dict()
        if bank_transaction and bank_transaction.get("paymentRef") != ref.id:
            return {"RspCode": "99", "Message": "Transaction already assigned"}
        if amount_vnd(booking.get("totalAmount")) != session["amountVnd"]:
            return {"RspCode": "04", "Message": "Order amount changed"}
        owns_slot = False
        if all(isinstance(booking.get(key), str) and booking[key] for key in ("pitchId", "bookingDate", "slotId")):
            day_ref = db.collection("pitches").document(booking["pitchId"]).collection("availability").document(booking["bookingDate"])
            day = day_ref.get(transaction=tx).to_dict() or {}
            lock = day.get("slots", {}).get(booking["slotId"], {})
            owns_slot = lock.get("bookingId") == session["bookingId"] and lock.get("status") in {"pending", "booked"}
        # Late/cancelled payments are real money received, but must not revive a released slot.
        review = (not owns_slot or booking.get("bookingStatus") not in {"pending", "approved"}
                  or booking.get("paymentStatus") != "unpaid"
                  or session["status"] == "failed"
                  or paid_at < session["createdAt"] or paid_at > session["expiresAt"])
        tx.set(bank_ref, {"paymentRef": ref.id, "bookingId": session["bookingId"], "createdAt": firestore.SERVER_TIMESTAMP})
        tx.update(ref, {"status": "paid", "transactionNo": transaction_no, "paidAt": paid_at,
                        "responseCode": response_code, "transactionStatus": transaction_status,
                        "updatedAt": firestore.SERVER_TIMESTAMP})
        tx.update(booking_ref, {"paymentStatus": "paid", "paymentMethod": "vnpay",
                                "vnpayTransactionId": transaction_no, "paymentReviewRequired": review,
                                "updatedAt": firestore.SERVER_TIMESTAMP})
        return {"RspCode": "00", "Message": "Confirm success"}

    return settle(db.transaction())
