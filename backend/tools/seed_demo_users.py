"""Create the demo Firebase Auth users and matching Firestore profiles.

This script must run with Firebase Admin SDK credentials on the backend machine.
It intentionally does not use the Android client or Firestore Security Rules.

Safe default: preview only.
Apply example:
    python -m tools.seed_demo_users \
        --project androidbookingfootballai \
        --password 123456 \
        --apply

Use --reset-password only when you explicitly want to set the password of an
already existing demo account. Use --force-profile only when an existing
profile has an intentionally different role and should be aligned to this
demo dataset.
"""

from __future__ import annotations

import argparse
import os
import re
import sys
from dataclasses import dataclass
from pathlib import Path

import firebase_admin
from dotenv import load_dotenv
from firebase_admin import auth, credentials, firestore


if hasattr(sys.stdout, "reconfigure"):
    # PowerShell trên Windows có thể dùng code page không hỗ trợ tiếng Việt.
    # UTF-8 giúp script in hướng dẫn/lỗi rõ ràng thay vì dừng bởi UnicodeEncodeError.
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    sys.stderr.reconfigure(encoding="utf-8", errors="replace")


BACKEND_DIR = Path(__file__).resolve().parents[1]
load_dotenv(BACKEND_DIR / ".env")


@dataclass(frozen=True)
class DemoUser:
    email: str
    display_name: str
    role: str
    phone_number: str


DEMO_USERS = (
    DemoUser("admin@test.com", "Admin System", "admin", "0900000000"),
    DemoUser("owner1@test.com", "Nguyễn Văn Bình", "owner", "0912345678"),
    DemoUser("owner2@test.com", "Trần Quang Đại", "owner", "0988111222"),
    DemoUser("owner3@test.com", "Lê Minh Phát", "owner", "0977333444"),
    DemoUser("customer@test.com", "Trần Minh Anh", "customer", "0987654321"),
)


def normalize_phone(phone: str) -> str:
    return re.sub(r"\D", "", phone)


def initialize_firebase(project_id: str) -> firebase_admin.App:
    if firebase_admin._apps:
        app = firebase_admin.get_app()
    else:
        credentials_path = os.getenv("GOOGLE_APPLICATION_CREDENTIALS")
        if credentials_path:
            credential = credentials.Certificate(credentials_path)
            credential_project = getattr(credential, "project_id", None)
            if credential_project and credential_project != project_id:
                raise RuntimeError(
                    "Service account thuộc project "
                    f"{credential_project}, không khớp project đích {project_id}."
                )
        else:
            credential = credentials.ApplicationDefault()
        app = firebase_admin.initialize_app(
            credential,
            {"projectId": project_id},
        )

    actual_project = app.project_id
    if actual_project and actual_project != project_id:
        raise RuntimeError(
            f"Firebase Admin đang trỏ tới {actual_project}, không phải {project_id}."
        )
    return app


def find_auth_user(email: str) -> auth.UserRecord | None:
    try:
        return auth.get_user_by_email(email)
    except auth.UserNotFoundError:
        return None


def build_profile(spec: DemoUser, uid: str, existing: dict | None) -> dict:
    profile = {
        "uid": uid,
        "email": spec.email,
        "displayName": spec.display_name,
        "phoneNumber": spec.phone_number,
        "avatarUrl": "",
        "fcmToken": "",
        "role": spec.role,
        "status": "active",
        "updatedAt": firestore.SERVER_TIMESTAMP,
    }
    if not existing or not existing.get("createdAt"):
        profile["createdAt"] = firestore.SERVER_TIMESTAMP

    if spec.role == "owner":
        profile.update({
            "bankName": "MB Bank",
            "bankAccountNumber": normalize_phone(spec.phone_number),
            "ownerApplicationStatus": "approved",
            "ownerDecisionReason": "Tài khoản mẫu được duyệt bởi seed script",
            "ownerReviewedBy": "seed-script",
            "ownerReviewedAt": firestore.SERVER_TIMESTAMP,
        })
    return profile


def preflight(db, *, force_profile: bool) -> list[tuple[DemoUser, auth.UserRecord | None, dict]]:
    plan = []
    for spec in DEMO_USERS:
        existing_auth = find_auth_user(spec.email)
        uid = existing_auth.uid if existing_auth else "(sẽ tạo mới)"
        existing_profile: dict = {}
        if existing_auth:
            snapshot = db.collection("users").document(existing_auth.uid).get()
            if snapshot.exists:
                existing_profile = snapshot.to_dict() or {}
                current_role = existing_profile.get("role")
                if current_role and current_role != spec.role and not force_profile:
                    raise RuntimeError(
                        f"{spec.email} đang có role={current_role!r}; "
                        "dùng --force-profile nếu chắc chắn muốn đổi role."
                    )
        print(f"- {spec.email:24} role={spec.role:8} uid={uid}")
        plan.append((spec, existing_auth, existing_profile))
    return plan


def apply_seed(db, plan, *, password: str, reset_password: bool) -> None:
    for spec, existing_auth, existing_profile in plan:
        if existing_auth is None:
            user = auth.create_user(
                email=spec.email,
                password=password,
                display_name=spec.display_name,
                disabled=False,
            )
            print(f"  CREATED Auth: {spec.email} ({user.uid})")
        else:
            user = existing_auth
            updates = {"display_name": spec.display_name}
            if reset_password:
                updates["password"] = password
            auth.update_user(user.uid, **updates)
            print(f"  REUSED Auth:  {spec.email} ({user.uid})")

        profile = build_profile(spec, user.uid, existing_profile)
        db.collection("users").document(user.uid).set(profile, merge=True)
        print(f"  SYNCED Firestore users/{user.uid} role={spec.role}")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--project", required=True, help="Firebase project ID đích")
    parser.add_argument("--password", required=True, help="Mật khẩu cho tài khoản mẫu, tối thiểu 6 ký tự")
    parser.add_argument("--apply", action="store_true", help="Thực sự tạo/cập nhật dữ liệu; mặc định chỉ xem trước")
    parser.add_argument("--reset-password", action="store_true",
                        help="Đặt lại mật khẩu cho tài khoản Auth đã tồn tại")
    parser.add_argument("--force-profile", action="store_true",
                        help="Cho phép đổi role Firestore hiện có của tài khoản mẫu")
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    if len(args.password) < 6:
        raise SystemExit("Mật khẩu Firebase phải có ít nhất 6 ký tự.")
    if args.reset_password and not args.apply:
        raise SystemExit("--reset-password chỉ có hiệu lực khi dùng cùng --apply.")

    print(f"Firebase project: {args.project}")
    print("Tài khoản seed:")

    initialize_firebase(args.project)
    db = firestore.client()
    plan = preflight(db, force_profile=args.force_profile)

    if not args.apply:
        print("DRY RUN: chưa tạo hoặc thay đổi tài khoản nào. Thêm --apply để thực thi.")
        return

    apply_seed(db, plan, password=args.password, reset_password=args.reset_password)
    print("Seed hoàn tất. Firebase Auth và Firestore users đã được đồng bộ.")


if __name__ == "__main__":
    main()
