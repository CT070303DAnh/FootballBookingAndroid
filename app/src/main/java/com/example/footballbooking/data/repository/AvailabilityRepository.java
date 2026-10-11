package com.example.footballbooking.data.repository;

import androidx.annotation.NonNull;

import com.example.footballbooking.data.model.Pitch;
import com.example.footballbooking.data.model.TimeSlot;
import com.example.footballbooking.utils.Constants;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/** Loads bookable pitches that are free for one fixed 90-minute slot. */
public class AvailabilityRepository {

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public Task<List<Pitch>> findAvailablePitches(String date, TimeSlot targetSlot) {
        if (date == null || targetSlot == null || targetSlot.getSlotId() == null) {
            return Tasks.forException(new IllegalArgumentException("Ngày hoặc slot không hợp lệ"));
        }

        return db.collection(Constants.COL_PITCHES)
                .whereEqualTo("status", Constants.PITCH_AVAILABLE)
                .get()
                .continueWithTask(task -> {
                    if (!task.isSuccessful()) {
                        Exception error = task.getException();
                        throw error != null ? error : new IllegalStateException("Không tải được danh sách sân");
                    }

                    List<Task<Pitch>> checks = new ArrayList<>();
                    for (DocumentSnapshot document : task.getResult().getDocuments()) {
                        Pitch pitch = document.toObject(Pitch.class);
                        if (pitch == null) continue;
                        if (pitch.getPitchId() == null || pitch.getPitchId().trim().isEmpty()) {
                            pitch.setPitchId(document.getId());
                        }
                        checks.add(checkPitch(date, targetSlot, pitch));
                    }

                    return Tasks.whenAllSuccess(checks).continueWith(completed -> {
                        List<Pitch> available = new ArrayList<>();
                        for (Object value : completed.getResult()) {
                            if (value instanceof Pitch) available.add((Pitch) value);
                        }
                        available.sort(Comparator.comparing(Pitch::getRating).reversed());
                        return available;
                    });
                });
    }

    private Task<Pitch> checkPitch(String date, TimeSlot targetSlot, Pitch pitch) {
        if (pitch.getBookingPolicyVersion() != 2) {
            return Tasks.forResult(null);
        }

        Task<QuerySnapshot> configuredSlots = db.collection(Constants.COL_PITCHES)
                .document(pitch.getPitchId())
                .collection(Constants.SUB_TIME_SLOTS)
                .get();
        Task<DocumentSnapshot> availability = db.collection(Constants.COL_PITCHES)
                .document(pitch.getPitchId())
                .collection(Constants.SUB_AVAILABILITY)
                .document(date)
                .get();

        return Tasks.whenAllSuccess(configuredSlots, availability).continueWith(task -> {
            List<Object> values = task.getResult();
            QuerySnapshot slotsSnapshot = (QuerySnapshot) values.get(0);
            DocumentSnapshot availabilityDocument = (DocumentSnapshot) values.get(1);

            if (!hasFixedSlot(slotsSnapshot, pitch, targetSlot)) return null;
            if (isPast(date, targetSlot.getStartTime())) return null;
            if (isOccupied(availabilityDocument, targetSlot)) return null;
            return pitch;
        });
    }

    private boolean hasFixedSlot(QuerySnapshot snapshot, Pitch pitch, TimeSlot target) {
        if (snapshot == null || snapshot.isEmpty()) {
            return pitch.getUseDefaultSlots();
        }
        for (DocumentSnapshot document : snapshot.getDocuments()) {
            Boolean active = document.getBoolean("isActive");
            String start = document.getString("startTime");
            String end = document.getString("endTime");
            if (Boolean.TRUE.equals(active)
                    && target.getSlotId().equals(document.getId())
                    && target.getStartTime().equals(start)
                    && target.getEndTime().equals(end)) {
                return true;
            }
        }
        return false;
    }

    private boolean isOccupied(DocumentSnapshot availability, TimeSlot target) {
        if (availability == null || !availability.exists()) return false;
        Object rawSlots = availability.get("slots");
        if (!(rawSlots instanceof Map)) return false;

        Map<?, ?> slots = (Map<?, ?>) rawSlots;
        Object rawState = slots.get(target.getSlotId());
        if (!(rawState instanceof Map)) return false;
        Object status = ((Map<?, ?>) rawState).get("status");
        if (status == null) return false;
        String normalized = String.valueOf(status).trim().toLowerCase(Locale.ROOT);
        return normalized.equals("pending")
                || normalized.equals("reserved")
                || normalized.equals("booked")
                || normalized.equals("held")
                || normalized.equals("occupied");
    }

    private boolean isPast(String date, String startTime) {
        TimeZone zone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh");
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.US);
        dateFormat.setTimeZone(zone);
        timeFormat.setTimeZone(zone);

        Date now = new Date();
        String today = dateFormat.format(now);
        if (date.compareTo(today) < 0) return true;
        return date.equals(today) && startTime != null
                && startTime.compareTo(timeFormat.format(now)) <= 0;
    }
}
