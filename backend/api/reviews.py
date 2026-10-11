import logging

from fastapi import APIRouter, Depends, Header, HTTPException
from pydantic import BaseModel, ConfigDict, Field

from services.firestore_service import verify_firebase_token
from services.review_service import ReviewError, create_review

router = APIRouter(prefix="/reviews", tags=["reviews"])
logger = logging.getLogger(__name__)


class ReviewRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")
    bookingId: str = Field(min_length=1, max_length=128)
    rating: float = Field(ge=1, le=5)
    comment: str = Field(default="", max_length=1000)


def customer_uid(authorization: str | None = Header(default=None)) -> str:
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(401, "Thiếu Firebase ID token")
    try:
        return verify_firebase_token(authorization[7:], check_revoked=True)["uid"]
    except Exception:
        raise HTTPException(401, "Phiên đăng nhập không hợp lệ") from None


@router.post("")
def post_review(payload: ReviewRequest, uid: str = Depends(customer_uid)):
    try:
        return create_review(uid, payload.bookingId, payload.rating, payload.comment)
    except ReviewError as exc:
        raise HTTPException(exc.status, str(exc)) from None
    except Exception:
        logger.exception("Review creation failed")
        raise HTTPException(503, "Chưa lưu được đánh giá. Vui lòng thử lại.") from None
