package com.example.footballbooking.utils;

import android.util.Log;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.Source;
import com.google.firebase.firestore.Query;
import com.example.footballbooking.data.repository.BookingRepository;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * BookingExpiryWorker — Tu dong tu choi don dat san qua gio chua duyet.
 */
public class BookingExpiryWorker {

    private static final String TAG = "ExpiryWorker";

    public static void autoRejectExpiredBookings(Runnable onComplete) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            if (onComplete != null) onComplete.run();
            return;
        }
        db.collection(Constants.COL_USERS).document(user.getUid()).get(Source.SERVER)
                .addOnSuccessListener(profile -> {
                    String role = profile.getString("role");
                    if ("blocked".equals(profile.getString("status"))
                            || !("admin".equals(role) || "owner".equals(role))) {
                        if (onComplete != null) onComplete.run();
                        return;
                    }
                    Query query = db.collection(Constants.COL_BOOKINGS)
                            .whereEqualTo("bookingStatus", Constants.STATUS_PENDING);
                    if ("owner".equals(role)) query = query.whereEqualTo("pitchOwnerId", user.getUid());
                    rejectExpired(query, onComplete);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Không thể kiểm tra quyền xử lý đơn hết hạn", e);
                    if (onComplete != null) onComplete.run();
                });
    }

    private static void rejectExpired(Query query, Runnable onComplete) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.US);
        dateFormat.setTimeZone(java.util.TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
        timeFormat.setTimeZone(java.util.TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
        Date now = new Date();
        String nowDate = dateFormat.format(now);
        String nowTime = timeFormat.format(now);

        Log.d(TAG, "Dang kiem tra don qua han... [" + nowDate + " " + nowTime + "]");

        query.get(Source.SERVER)
                .addOnSuccessListener(snapshot -> {
                    if (snapshot == null || snapshot.isEmpty()) {
                        Log.d(TAG, "Khong co don pending nao can kiem tra.");
                        if (onComplete != null) onComplete.run();
                        return;
                    }

                    List<Task<Void>> operations = new ArrayList<>();

                    for (QueryDocumentSnapshot doc : snapshot) {
                        String bookingDate = doc.getString("bookingDate");
                        String endTime    = doc.getString("endTime");
                        String startTime  = doc.getString("startTime");
                        String bookingId  = doc.getId();

                        String compareTime = (endTime != null && !endTime.isEmpty()) ? endTime : startTime;

                        if (bookingDate == null || compareTime == null) continue;

                        boolean isPastDate = bookingDate.compareTo(nowDate) < 0;
                        boolean isTodayPast = bookingDate.equals(nowDate)
                                && compareTime.compareTo(nowTime) <= 0;

                        if (!isPastDate && !isTodayPast) continue;

                        Log.d(TAG, "Tu dong tu choi don: " + bookingId
                                + " [" + bookingDate + " " + compareTime + "]");
                        operations.add(BookingRepository.getInstance().transitionBooking(bookingId,
                                Constants.STATUS_REJECTED, "Đơn đã quá giờ và chưa được duyệt."));
                    }

                    if (operations.isEmpty()) {
                        Log.d(TAG, "Khong co don nao qua han.");
                        if (onComplete != null) onComplete.run();
                        return;
                    }

                    Tasks.whenAllComplete(operations)
                            .addOnCompleteListener(unused -> {
                                for (Task<Void> operation : operations) {
                                    if (!operation.isSuccessful()) Log.e(TAG, "Không thể từ chối đơn hết hạn", operation.getException());
                                }
                                if (onComplete != null) onComplete.run();
                            });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Loi query don pending: " + e.getMessage());
                    if (onComplete != null) onComplete.run();
                });
    }
}
