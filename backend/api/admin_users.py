"""Admin-only user CRUD backed by Firebase Admin SDK.

The Android client must not create or delete other Firebase Auth users with the
client SDK. These operations are kept on the trusted backend and update Auth
and the matching Firestore profile together.
"""

import re
from typing import Any

from fastapi import APIRouter, Depends, Header, HTTPException
from firebase_admin import auth, firestore
from pydantic import BaseModel, ConfigDict, Field

from services.firestore_service import get_db, get_user, verify_firebase_token

router = APIRouter(prefix="/admin/users", tags=["admin-users"])

ALLOWED_ROLES = {"customer", "owner", "owner_pending", "admin"}
ALLOWED_STATUSES = {"active", "blocked"}
EMAIL_RE = re.compile(r"^[^\s@]+@[^\s@]+\.[^\s@]+$")


class CreateUserRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")
    email: str = Field(min_length=3, max_length=254)
    password: str = Field(min_length=6, max_length=128)
    displayName: str = Field(min_length=1, max_length=120)
    phoneNumber: str = Field(default="", max_length=30)
    role: str = Field(default="customer", max_length=30)
    status: str = Field(default="active", max_length=20)


class UpdateUserRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")
    email: str = Field(min_length=3, max_length=254)
    password: str | None = Field(default=None, max_length=128)
    displayName: str = Field(min_length=1, max_length=120)
    phoneNumber: str = Field(default="", max_length=30)
    role: str = Field(max_length=30)
    status: str = Field(max_length=20)


def admin_uid(authorization: str | None = Header(default=None)) -> str:
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(401, "Thiếu Firebase ID token")
    try:
        claims = verify_firebase_token(authorization[7:], check_revoked=True)
        profile = get_user(claims["uid"])
    except Exception:
        raise HTTPException(401, "Phiên đăng nhập không hợp lệ") from None
    if profile.get("role") != "admin" or profile.get("status", "active") != "active":
        raise HTTPException(403, "Chỉ quản trị viên đang hoạt động mới được quản lý tài khoản")
    return claims["uid"]


def _validate_common(email: str, role: str, status: str) -> tuple[str, str, str]:
    normalized_email = email.strip().lower()
    if not EMAIL_RE.fullmatch(normalized_email):
        raise HTTPException(400, "Email không hợp lệ")
    if role not in ALLOWED_ROLES:
        raise HTTPException(400, "Vai trò tài khoản không hợp lệ")
    if status not in ALLOWED_STATUSES:
        raise HTTPException(400, "Trạng thái tài khoản không hợp lệ")
    return normalized_email, role, status


def _profile_fields(uid: str, payload: CreateUserRequest | UpdateUserRequest,
                    *, created: bool = False) -> dict[str, Any]:
    email, role, status = _validate_common(payload.email, payload.role, payload.status)
    phone = payload.phoneNumber.strip()
    owner_application = (
        "approved" if role == "owner"
        else "pending" if role == "owner_pending"
        else ""
    )
    fields: dict[str, Any] = {
        "uid": uid,
        "email": email,
        "displayName": payload.displayName.strip(),
        "phoneNumber": phone,
        "role": role,
        "status": status,
        "bankName": "MB Bank" if role == "owner" else "",
        "bankAccountNumber": re.sub(r"\D", "", phone) if role == "owner" else "",
        "ownerApplicationStatus": owner_application,
        "ownerDecisionReason": "",
        "updatedAt": firestore.SERVER_TIMESTAMP,
    }
    if created:
        fields.update({
            "avatarUrl": "",
            "fcmToken": "",
            "createdAt": firestore.SERVER_TIMESTAMP,
        })
    return fields


def _target_profile(uid: str, current_admin_uid: str) -> dict[str, Any]:
    if not uid or uid == current_admin_uid:
        raise HTTPException(400, "Không thể thao tác với tài khoản Admin hiện tại")
    profile = get_user(uid)
    if profile.get("role") == "admin":
        raise HTTPException(403, "Không thể sửa hoặc xóa tài khoản Admin")
    return profile


@router.post("", status_code=201)
def create_user(payload: CreateUserRequest, _: str = Depends(admin_uid)):
    _validate_common(payload.email, payload.role, payload.status)
    record = None
    try:
        record = auth.create_user(
            email=payload.email.strip().lower(),
            password=payload.password,
            display_name=payload.displayName.strip(),
            disabled=payload.status == "blocked",
        )
        get_db().collection("users").document(record.uid).set(
            _profile_fields(record.uid, payload, created=True)
        )
        return {"ok": True, "uid": record.uid}
    except auth.EmailAlreadyExistsError:
        raise HTTPException(409, "Email đã tồn tại trong Firebase Authentication") from None
    except HTTPException:
        if record is not None:
            auth.delete_user(record.uid)
        raise
    except Exception as exc:
        if record is not None:
            try:
                auth.delete_user(record.uid)
            except Exception:
                pass
        raise HTTPException(503, "Không thể tạo tài khoản mới") from exc


@router.patch("/{uid}")
def update_user(uid: str, payload: UpdateUserRequest, admin_id: str = Depends(admin_uid)):
    _target_profile(uid, admin_id)
    email, role, status = _validate_common(payload.email, payload.role, payload.status)
    try:
        auth.get_user(uid)
        auth.update_user(
            uid,
            email=email,
            display_name=payload.displayName.strip(),
            password=payload.password if payload.password else None,
            disabled=status == "blocked",
        )
        get_db().collection("users").document(uid).update(
            _profile_fields(uid, payload)
        )
        return {"ok": True, "uid": uid}
    except auth.UserNotFoundError:
        raise HTTPException(404, "Không tìm thấy tài khoản Firebase") from None
    except auth.EmailAlreadyExistsError:
        raise HTTPException(409, "Email đã tồn tại trong Firebase Authentication") from None
    except HTTPException:
        raise
    except Exception as exc:
        raise HTTPException(503, "Không thể cập nhật tài khoản") from exc


@router.delete("/{uid}")
def delete_user(uid: str, admin_id: str = Depends(admin_uid)):
    profile = _target_profile(uid, admin_id)
    if profile.get("role") == "owner":
        pitch_query = get_db().collection("pitches").where("ownerId", "==", uid).limit(1).get()
        if pitch_query:
            raise HTTPException(409, "Chủ sân vẫn còn sân. Hãy ẩn hoặc chuyển sân trước khi xóa tài khoản.")
    try:
        auth.delete_user(uid)
        get_db().collection("users").document(uid).delete()
        return {"ok": True, "uid": uid}
    except auth.UserNotFoundError:
        get_db().collection("users").document(uid).delete()
        return {"ok": True, "uid": uid}
    except Exception as exc:
        raise HTTPException(503, "Không thể xóa tài khoản") from exc
