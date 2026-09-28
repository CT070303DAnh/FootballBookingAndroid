package com.example.footballbooking.utils;

import android.util.Log;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.WriteBatch;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * BookingExpiryWorker — Tu dong tu choi don dat san qua gio chua duyet.
 *
 * LOGIC:
 * - Query tat ca booking co bookingStatus = "pending"
 * - Voi moi don, ghep bookingDate + endTime thanh datetime
 * - Neu datetime do da qua thoi diem hien tai -> reject don + tra slot ve available
 *
 * CACH GOI (tu SplashActivity hoac khi mo Dashboard):
 *   BookingExpiryWorker.autoRejectExpiredBookings(null);
 */
public class BookingExpiryWorker {

    private static final String TAG = "ExpiryWorker";

    /**
     * Chay auto-reject. Callback onComplete (nullable) duoc goi sau khi xu ly xong.
     */
    public static void autoRejectExpiredBookings(Runnable onComplete) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        String nowDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        String nowTime = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());

        Log.d(TAG, "Dang kiem tra don qua han... [" + nowDate + " " + nowTime + "]");

        db.collection(Constants.COL_BOOKINGS)
                .whereEqualTo("bookingStatus", Constants.STATUS_PENDING)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot == null || snapshot.isEmpty()) {
                        Log.d(TAG, "Khong co don pending nao can kiem tra.");
                        if (onComplete != null) onComplete.run();
                        return;
                    }

                    WriteBatch batch = db.batch();
                    int[] expiredCount = {0};

                    for (QueryDocumentSnapshot doc : snapshot) {
                        String bookingDate = doc.getString("bookingDate");
                        String endTime    = doc.getString("endTime");
                        String startTime  = doc.getString("startTime");
                        String pitchId    = doc.getString("pitchId");
                        String slotId     = doc.getString("slotId");
                        String bookingId  = doc.getId();

                        String compareTime = (endTime != null && !endTime.isEmpty()) ? endTime : startTime;

                        if (bookingDate == null || compareTime == null) continue;

                        boolean isPastDate = bookingDate.compareTo(nowDate) < 0;
                        boolean isTodayPast = bookingDate.equals(nowDate)
                                && compareTime.compareTo(nowTime) <= 0;

                        if (!isPastDate && !isTodayPast) continue;

                        Log.d(TAG, "Tu dong tu choi don: " + bookingId
                                + " [" + bookingDate + " " + compareTime + "]");
                        expiredCount[0]++;

                        Map<String, Object> bookingUpdate = new HashMap<>();
                        bookingUpdate.put("bookingStatus", Constants.STATUS_REJECTED);
                        bookingUpdate.put("matchStatus", Constants.MATCH_FINISHED);
                        bookingUpdate.put("rejectReason", "Don dat san da qua gio va chua duoc duyet, he thong tu dong tu choi.");
                        batch.update(
                                db.collection(Constants.COL_BOOKINGS).document(bookingId),
                                bookingUpdate
                        );

                        if (pitchId != null && slotId != null) {
                            Map<String, Object> slotDetails = new HashMap<>();
                            slotDetails.put("status", "available");
                            slotDetails.put("bookingId", null);

                            Map<String, Object> slotsMap = new HashMap<>();
                            slotsMap.put(slotId, slotDetails);

                            Map<String, Object> availUpdate = new HashMap<>();
                            availUpdate.put("slots", slotsMap);

                            batch.set(
                                    db.collection(Constants.COL_PITCHES)
                                      .document(pitchId)
                                      .collection(Constants.SUB_AVAILABILITY)
                                      .document(bookingDate),
                                    availUpdate,
                                    com.google.firebase.firestore.SetOptions.merge()
                            );
                        }
                    }

                    if (expiredCount[0] == 0) {
                        Log.d(TAG, "Khong co don nao qua han.");
                        if (onComplete != null) onComplete.run();
                        return;
                    }

                    batch.commit()
                            .addOnSuccessListener(unused -> {
                                Log.d(TAG, "Da tu dong tu choi " + expiredCount[0] + " don qua han.");
                                if (onComplete != null) onComplete.run();
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Loi khi commit batch reject: " + e.getMessage());
                                if (onComplete != null) onComplete.run();
                            });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Loi query don pending: " + e.getMessage());
                    if (onComplete != null) onComplete.run();
                });
    }
}
