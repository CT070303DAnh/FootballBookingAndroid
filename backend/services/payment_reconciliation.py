"""VNPay QueryDR 2.1.0. Never treat transport errors as payment failures.

Reference: https://sandbox.vnpayment.vn/apis/docs/truy-van-hoan-tien/querydr&refund.html
"""
from datetime import datetime, timedelta, timezone
import hashlib
import hmac
import ipaddress
import os
import re
import uuid

import httpx
from firebase_admin import firestore
from services.payment_service import (
    Settings, PaymentError, VIETNAM, get_db, authorize, payment_status, settle_verified,
)

REQUEST_FIELDS = ("vnp_RequestId", "vnp_Version", "vnp_Command", "vnp_TmnCode", "vnp_TxnRef",
                  "vnp_TransactionDate", "vnp_CreateDate", "vnp_IpAddr", "vnp_OrderInfo")
RESPONSE_FIELDS = ("vnp_ResponseId", "vnp_Command", "vnp_ResponseCode", "vnp_Message", "vnp_TmnCode",
                   "vnp_TxnRef", "vnp_Amount", "vnp_BankCode", "vnp_PayDate", "vnp_TransactionNo",
                   "vnp_TransactionType", "vnp_TransactionStatus", "vnp_OrderInfo",
                   "vnp_PromotionCode", "vnp_PromotionAmount")


def query_signature(data, fields, secret):
    values = [data.get(field, "") for field in fields]
    if any(not isinstance(value, str) or "|" in value for value in values):
        raise PaymentError("Dữ liệu đối soát không hợp lệ", 502)
    return hmac.new(secret.encode(), "|".join(values).encode("utf-8"), hashlib.sha512).hexdigest()


def query_gateway(session, config):
    server_ip = os.getenv("VNPAY_SERVER_IP", "")
    try:
        ipaddress.ip_address(server_ip)
    except ValueError:
        raise PaymentError("Chưa cấu hình địa chỉ máy chủ đối soát", 503) from None
    endpoint = ("https://sandbox.vnpayment.vn/merchant_webapi/api/transaction"
                if "sandbox.vnpayment.vn" in config.gateway else "https://pay.vnpay.vn/merchant_webapi/api/transaction")
    payload = {
        "vnp_RequestId": uuid.uuid4().hex, "vnp_Version": "2.1.0", "vnp_Command": "querydr",
        "vnp_TmnCode": config.tmn_code, "vnp_TxnRef": session["paymentRef"],
        "vnp_TransactionDate": session["createdAt"].astimezone(VIETNAM).strftime("%Y%m%d%H%M%S"),
        "vnp_CreateDate": datetime.now(VIETNAM).strftime("%Y%m%d%H%M%S"),
        "vnp_IpAddr": server_ip, "vnp_OrderInfo": "Kiem tra " + session["paymentRef"],
    }
    payload["vnp_SecureHash"] = query_signature(payload, REQUEST_FIELDS, config.secret)
    try:
        response = httpx.post(endpoint, json=payload, timeout=10, follow_redirects=False)
        response.raise_for_status()
        data = response.json()
    except (httpx.HTTPError, ValueError):
        raise PaymentError("Chưa kết nối được VNPay để đối soát. Vui lòng kiểm tra lại sau.", 503) from None
    if not isinstance(data, dict):
        raise PaymentError("Phản hồi đối soát không hợp lệ", 502)
    signature = data.get("vnp_SecureHash", "")
    if (not isinstance(signature, str) or not re.fullmatch(r"[a-fA-F0-9]{128}", signature)
            or not hmac.compare_digest(signature.lower(), query_signature(data, RESPONSE_FIELDS, config.secret))):
        raise PaymentError("Không xác minh được chữ ký đối soát", 502)
    if (data.get("vnp_TmnCode") != config.tmn_code or data.get("vnp_TxnRef") != session["paymentRef"]
            or data.get("vnp_Command") != "querydr"):
        raise PaymentError("Giao dịch đối soát không khớp", 502)
    # 91/not found, 94/rate limited and pending are not proof that no money moved.
    if data.get("vnp_ResponseCode") != "00":
        raise PaymentError("VNPay chưa xác nhận được giao dịch. Vui lòng kiểm tra lại sau.", 503)
    if (data.get("vnp_TransactionType") != "01"
            or not re.fullmatch(r"[0-9]{1,12}", data.get("vnp_Amount", ""))
            or int(data["vnp_Amount"]) != session["amountVnd"] * 100):
        raise PaymentError("Số tiền hoặc loại giao dịch đối soát không khớp", 502)
    return data


def reconcile_session(uid, payment_ref):
    _reconcile(payment_ref, uid=uid)
    return payment_status(uid, payment_ref)


def reconcile_verified_return(params):
    """Only called after verified_callback; browser fields never settle money."""
    _reconcile(params["vnp_TxnRef"], return_params=params)


def _reconcile(payment_ref, uid=None, return_params=None):
    """Authenticated manual/worker reconciliation with a cross-process rate limit."""
    config = Settings.load()
    db = get_db()
    ref = db.collection("payment_sessions").document(payment_ref)

    @firestore.transactional
    def acquire(tx):
        session = ref.get(transaction=tx).to_dict()
        if not session or (return_params is None and session.get("customerId") != uid):
            raise PaymentError("Không tìm thấy giao dịch", 404)
        if return_params is not None:
            amount = return_params.get("vnp_Amount", "")
            if (session.get("tmnCode") != config.tmn_code
                    or return_params.get("vnp_TmnCode") != session.get("tmnCode")
                    or not re.fullmatch(r"[0-9]{1,12}", amount)
                    or int(amount) != session["amountVnd"] * 100):
                raise PaymentError("Giao dịch trả về không khớp", 400)
        else:
            booking = db.collection("bookings").document(session["bookingId"]).get(transaction=tx).to_dict()
            user = db.collection("users").document(uid).get(transaction=tx).to_dict()
            authorize(uid, user, booking)
        if session["status"] == "paid":
            return None
        now = datetime.now(timezone.utc)
        if now - session.get("lastQueryAt", session["createdAt"]) < timedelta(seconds=60):
            return None
        tx.update(ref, {"lastQueryAt": now})
        return session

    session = acquire(db.transaction())
    if session:
        data = query_gateway(session, config)
        if data.get("vnp_TransactionStatus") in {"00", "02"}:
            # QueryDR responseCode means query success; transactionStatus describes payment.
            result = settle_verified(data, config)
            if result["RspCode"] not in {"00", "02"}:
                raise PaymentError("Giao dịch cần được quản trị viên đối soát", 409)
