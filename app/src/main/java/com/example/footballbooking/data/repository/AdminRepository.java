package com.example.footballbooking.data.repository;

import androidx.lifecycle.MutableLiveData;

import com.example.footballbooking.data.model.Booking;
import com.example.footballbooking.data.model.Pitch;
import com.example.footballbooking.data.model.User;
import com.example.footballbooking.utils.Constants;
import com.example.footballbooking.utils.Resource;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.List;

/**
 * AdminRepository — Chứa logic thao tác dữ liệu dành riêng cho Admin.
 */
public class AdminRepository {

    private final FirebaseFirestore mDb;

    private ListenerRegistration allBookingsListener;
    private ListenerRegistration usersListener;
    private ListenerRegistration pitchesListener;

    private static AdminRepository instance;

    private AdminRepository() {
        mDb = FirebaseFirestore.getInstance();
    }

    public static synchronized AdminRepository getInstance() {
        if (instance == null) instance = new AdminRepository();
        return instance;
    }

    // ============================================================
    // SYSTEM DASHBOARD (KPIs)
    // ============================================================

    public void getBookingsByMonth(String yearMonth,
                                   MutableLiveData<Resource<List<Booking>>> result) {
        result.setValue(Resource.loading(null));

        String startDate = yearMonth + "-01";
        String endDate   = yearMonth + "-31";

        mDb.collection(Constants.COL_BOOKINGS)
                .whereGreaterThanOrEqualTo("bookingDate", startDate)
                .whereLessThanOrEqualTo("bookingDate", endDate)
                .whereEqualTo("bookingStatus", Constants.STATUS_APPROVED)
                .get()
                .addOnSuccessListener(snap ->
                        result.setValue(Resource.success(snap.toObjects(Booking.class))))
                .addOnFailureListener(e ->
                        result.setValue(Resource.error(e.getMessage(), null)));
    }

    // ============================================================
    // USER MANAGEMENT
    // ============================================================

    public void getAllUsers(MutableLiveData<Resource<List<User>>> result) {
        result.setValue(Resource.loading(null));
        detachUsersListener();

        usersListener = mDb.collection(Constants.COL_USERS)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        result.setValue(Resource.error(error.getMessage(), null));
                        return;
                    }
                    List<User> users = snapshots != null
                            ? snapshots.toObjects(User.class) : null;
                    result.setValue(Resource.success(users));
                });
    }

    public void updateUserStatus(String userId, String status, MutableLiveData<Resource<Boolean>> result) {
        result.setValue(Resource.loading(null));
        mDb.collection(Constants.COL_USERS).document(userId)
                .update("status", status)
                .addOnSuccessListener(aVoid -> result.setValue(Resource.success(true)))
                .addOnFailureListener(e -> result.setValue(Resource.error(e.getMessage(), false)));
    }

    // ============================================================
    // PITCH MODERATION
    // ============================================================

    public void getAllPitchesForModeration(MutableLiveData<Resource<List<Pitch>>> result) {
        result.setValue(Resource.loading(null));
        detachPitchesListener();

        pitchesListener = mDb.collection(Constants.COL_PITCHES)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        result.setValue(Resource.error(error.getMessage(), null));
                        return;
                    }
                    List<Pitch> pitches = snapshots != null
                            ? snapshots.toObjects(Pitch.class) : null;
                    result.setValue(Resource.success(pitches));
                });
    }

    public void updatePitchStatus(String pitchId, String status, MutableLiveData<Resource<Boolean>> result) {
        result.setValue(Resource.loading(null));
        mDb.collection(Constants.COL_PITCHES).document(pitchId)
                .update("status", status)
                .addOnSuccessListener(aVoid -> result.setValue(Resource.success(true)))
                .addOnFailureListener(e -> result.setValue(Resource.error(e.getMessage(), false)));
    }

    // ============================================================
    // BOOKING MONITORING (View Only)
    // ============================================================

    public void getAllBookings(MutableLiveData<Resource<List<Booking>>> result) {
        result.setValue(Resource.loading(null));
        detachAllBookingsListener();

        allBookingsListener = mDb.collection(Constants.COL_BOOKINGS)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        result.setValue(Resource.error(error.getMessage(), null));
                        return;
                    }
                    List<Booking> bookings = snapshots != null
                            ? snapshots.toObjects(Booking.class) : null;
                    result.setValue(Resource.success(bookings));
                });
    }

    // ============================================================
    // CLEANUP
    // ============================================================

    public void detachAllBookingsListener() {
        if (allBookingsListener != null) {
            allBookingsListener.remove();
            allBookingsListener = null;
        }
    }

    public void detachUsersListener() {
        if (usersListener != null) {
            usersListener.remove();
            usersListener = null;
        }
    }

    public void detachPitchesListener() {
        if (pitchesListener != null) {
            pitchesListener.remove();
            pitchesListener = null;
        }
    }
}
