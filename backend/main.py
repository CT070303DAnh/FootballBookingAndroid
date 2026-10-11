import os
from pathlib import Path

from dotenv import load_dotenv
from fastapi import FastAPI

load_dotenv(Path(__file__).parent / ".env")

from api.payments import router as payments_router
from api.payos import router as payos_router
from api.reviews import router as reviews_router
from services.payos_service import PaymentError, Settings as PayOSSettings

app = FastAPI(title="Football Booking Backend", version="0.1.0")
app.include_router(payments_router)
app.include_router(payos_router)
app.include_router(reviews_router)


@app.get("/health")
def health() -> dict[str, str | bool]:
    try:
        PayOSSettings.load()
        payment_configured = True
    except PaymentError:
        payment_configured = False
    return {
        "ok": True,
        "ready": payment_configured,
        "provider": "Firebase/PayOS",
        "paymentConfigured": payment_configured,
    }
