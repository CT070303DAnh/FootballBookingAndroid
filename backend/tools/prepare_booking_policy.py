"""Audit/rebuild availability before enabling C2. Default is read-only.

Run from backend: python -m tools.prepare_booking_policy --project PROJECT_ID
Stop all old clients/server writers before --apply; keep bookings disabled during maintenance.
The explicit project must match the intended Firebase project. No credentials are bundled.
"""
import argparse
from collections import defaultdict
from datetime import date, datetime
import math
from zoneinfo import ZoneInfo

import firebase_admin
from firebase_admin import firestore

from services.firestore_service import DEFAULT_SLOTS


def build_plan(pitch_id, pitch, slots, bookings, availability):
    """Pure validation: reject ambiguous occupancy instead of silently choosing a winner."""
    if not isinstance(pitch.get("ownerId"), str) or not pitch["ownerId"]:
        raise ValueError(f"{pitch_id}: missing ownerId")
    price = pitch.get("basePrice")
    if not isinstance(price, (float, int)) or not math.isfinite(price) or price < 0:
        raise ValueError(f"{pitch_id}: invalid basePrice")
    catalog = slots or {s["slotId"]: {**s, "isActive": True} for s in DEFAULT_SLOTS}
    active = []
    for slot_id, slot in catalog.items():
        if not slot.get("isActive", False):
            continue
        start = datetime.strptime(slot["startTime"], "%H:%M").strftime("%H:%M")
        end = datetime.strptime(slot["endTime"], "%H:%M").strftime("%H:%M")
        if start != slot["startTime"] or end != slot["endTime"] or start >= end:
            raise ValueError(f"{pitch_id}/{slot_id}: invalid time range")
        for other_id, other_start, other_end in active:
            if start < other_end and other_start < end:
                raise ValueError(f"{pitch_id}: overlapping slots {slot_id}, {other_id}")
        active.append((slot_id, start, end))
    days = defaultdict(dict)
    today = datetime.now(ZoneInfo("Asia/Ho_Chi_Minh")).date().isoformat()
    for booking_id, booking in bookings.items():
        if booking.get("bookingStatus") not in {"pending", "approved", "cancelled", "rejected"}:
            raise ValueError(f"{booking_id}: unknown booking state; review manually")
        if booking.get("bookingStatus") not in {"pending", "approved"}:
            continue
        booking_date, slot_id = booking["bookingDate"], booking["slotId"]
        if date.fromisoformat(booking_date).isoformat() != booking_date:
            raise ValueError(f"{booking_id}: invalid date")
        if booking.get("pitchOwnerId") != pitch["ownerId"]:
            raise ValueError(f"{booking_id}: owner mismatch; review manually")
        # Future live bookings must still correspond to the current schedule.
        if booking_date >= today:
            slot = catalog.get(slot_id, {})
            if (not slot.get("isActive", False) or slot.get("startTime") != booking.get("startTime")
                    or slot.get("endTime") != booking.get("endTime")):
                raise ValueError(f"{booking_id}: live booking does not match current schedule")
        if slot_id in days[booking_date]:
            raise ValueError(f"{pitch_id}/{booking_date}/{slot_id}: multiple live bookings; review manually")
        days[booking_date][slot_id] = {
            "status": "pending" if booking["bookingStatus"] == "pending" else "booked",
            "bookingId": booking_id,
        }
    # Do not discard reserved/manual/orphan locks; they may represent real reservations.
    for day_id, old in availability.items():
        days[day_id]  # Retain a day even if all former bookings were cancelled.
        for slot_id, state in old.get("slots", {}).items():
            if state.get("status") not in {"available", "cancelled", "rejected"}:
                booking_id = state.get("bookingId")
                if booking_id not in bookings:
                    raise ValueError(f"{pitch_id}/{day_id}/{slot_id}: orphan/manual lock; review manually")
                b = bookings[booking_id]
                if b.get("bookingDate") != day_id or b.get("slotId") != slot_id:
                    raise ValueError(f"{booking_id}: lock points to the wrong date/slot")
    return {day_id: {"date": day_id, "slots": states,
                     "lastSlotId": next(iter(states), "")}
            for day_id, states in days.items()}


def validate_fixed_slots(pitch_id, slots):
    """Reject legacy schedules that do not belong to the 90-minute catalog."""
    expected = {slot["slotId"]: slot for slot in DEFAULT_SLOTS}
    for slot_id, slot in slots.items():
        policy = expected.get(slot_id)
        if (policy is None or slot.get("startTime") != policy["startTime"]
                or slot.get("endTime") != policy["endTime"]):
            raise ValueError(f"{pitch_id}/{slot_id}: slot must use the fixed 90-minute catalog")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--project", required=True)
    parser.add_argument("--apply", action="store_true", help="Write after review, with all booking writers stopped")
    args = parser.parse_args()
    firebase_admin.initialize_app(options={"projectId": args.project})
    db = firestore.client()
    pitches = list(db.collection("pitches").stream())
    if args.apply:
        # Fail closed, including if validation or the subsequent writes fail.
        for pitch in pitches:
            pitch.reference.update({"bookingPolicyVersion": 0})
    all_bookings = defaultdict(dict)
    for snapshot in db.collection("bookings").stream():
        b = snapshot.to_dict()
        all_bookings[b["pitchId"]][snapshot.id] = b
    plans = []
    for pitch in pitches:
        slots = {d.id: d.to_dict() for d in pitch.reference.collection("timeSlots").stream()}
        validate_fixed_slots(pitch.id, slots)
        old_days = {d.id: d.to_dict() for d in pitch.reference.collection("availability").stream()}
        plan = build_plan(pitch.id, pitch.to_dict(), slots, all_bookings[pitch.id], old_days)
        plans.append((pitch.reference, plan, not slots))
        print(f"{pitch.id}: {len(plan)} day documents, {'default' if not slots else 'configured'} schedule")
    if not args.apply:
        print("DRY RUN: no writes. Review all conflicts and stop writers before --apply.")
        return
    for ref, plan, defaults in plans:
        for day_id, data in plan.items():
            ref.collection("availability").document(day_id).set(data)
        ref.update({"bookingPolicyVersion": 2, "useDefaultSlots": defaults})
    print("Applied. Deploy only the compatible Android/backend clients before resuming booking traffic.")


if __name__ == "__main__":
    main()
