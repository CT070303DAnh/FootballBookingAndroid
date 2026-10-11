# FastAPI backend (Firebase + PayOS VietQR)

## Setup

1. Configure Firebase and PayOS in `.env` (`PAYOS_CLIENT_ID`, `PAYOS_API_KEY`,
   `PAYOS_CHECKSUM_KEY`, `PAYOS_RETURN_URL`, `PAYOS_CANCEL_URL`,
   `PAYOS_WEBHOOK_URL`).
2. Set `GOOGLE_APPLICATION_CREDENTIALS` to a Firebase service-account JSON file outside this repository. Never commit that file or `.env`.
3. Create and activate a Python virtual environment, then install dependencies:

```powershell
py -3.13 -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install -r requirements.txt
```

4. Start FastAPI from the `backend` directory:

```powershell
python -m uvicorn main:app --host 0.0.0.0 --port 8000
```

Check `http://127.0.0.1:8000/health` before testing PayOS. PayOS return,
cancel and webhook URLs must use a public HTTPS tunnel/domain. After the
backend and tunnel are running, register the webhook once:

```powershell
python -m tools.configure_payos_webhook
```

The Android emulator reaches the local backend through
`http://10.0.2.2:8000/`. For a physical phone, set
`BACKEND_BASE_URL=http://<PC-LAN-IP>:8000/` and
`PAYMENT_BASE_URL=http://<PC-LAN-IP>:8000/` in root `local.properties` and
allow port 8000 through the PC firewall.

## Notes

- Fixed-slot availability and price are read from Firestore. A booking is only written after the user confirms, and the backend rechecks current pitch/slot/service data in a Firestore transaction.
- `python -m unittest discover -s tests -v` runs offline unit/API tests without Firebase credentials or a running external model server.
