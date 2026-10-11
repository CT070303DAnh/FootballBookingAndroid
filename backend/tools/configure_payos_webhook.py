"""Register the public PayOS webhook URL for the configured payment channel."""

import asyncio
from pathlib import Path

from dotenv import load_dotenv
from payos import AsyncPayOS

from services.payos_service import Settings


async def main() -> None:
    load_dotenv(Path(__file__).resolve().parents[1] / ".env")
    settings = Settings.load()
    client = AsyncPayOS(
        client_id=settings.client_id,
        api_key=settings.api_key,
        checksum_key=settings.checksum_key,
    )
    try:
        result = await client.webhooks.confirm(settings.webhook_url)
        print("PayOS webhook đã được đăng ký:", result.webhook_url)
    finally:
        await client.aclose()


if __name__ == "__main__":
    asyncio.run(main())
