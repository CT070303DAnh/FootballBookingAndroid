# Firestore rules regression tests

These tests run against the **local Firestore Emulator** using the reserved demo
project `demo-football-booking`. They do not need Firebase login, a service account
or a production project. The test configuration is not a deployment configuration.

Requirements: Node.js 22 and Java 21 or newer available on PATH.

```powershell
cd tests/firestore
npm ci
npm test
```

The first run downloads the Firestore emulator. Port 8089 must be available.
`npm test` starts the emulator, loads the actual root `firestore.rules`, runs the
tests and stops the emulator. Test data is cleared between cases.

The suite covers the C1 user-profile authorization boundary:

- Customer and pending-owner registration with matching UID and active status.
- Rejection of self-created admin/owner roles, even with an admin-looking email.
- Rejection of self-promotion, self-unblocking and protected-field deletion.
- Rejection of cross-user edits and batched privilege escalation.
- Continued support for profile edits, FCM tokens and legitimate admin actions.
- Rejection of blocked-admin actions; compatibility with legacy admin profiles
  that do not yet have a status field.

The C2 booking suite also covers atomic booking/availability writes, catalog
pricing (up to five services), ownership and state transitions, stale cancellation,
concurrent reservations, migration gating and schedule-edit restrictions. Both
suites run sequentially because they clear the same demo database.

The C3 cases reject client writes to payment fields, payment sessions and bank
transaction records, including administrator clients. Verified payment fields
remain intact when a customer cancels a booking. Python payment tests verify
HMAC/API behavior and transactional settlement against the same local emulator.
Storage and FCM queue authorization remain separate findings.

To include real Python SDK transaction tests, start the local emulator with
`npx firebase emulators:start --config ../../firebase.test.json --project demo-football-booking --only firestore`.
Then, from the backend directory in a separate PowerShell terminal:

```powershell
$env:FIRESTORE_EMULATOR_HOST = '127.0.0.1:8089'
.\.venv\Scripts\python.exe -m unittest discover -s tests -v
```

Run Python tests after the Node suite, not concurrently. The Python integration
tests refuse to run unless the emulator host is exactly `127.0.0.1:8089` and always
use anonymous credentials with the fixed demo project.
