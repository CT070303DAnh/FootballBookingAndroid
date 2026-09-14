package com.example.footballbooking.data.repository;

import androidx.lifecycle.MutableLiveData;

import com.example.footballbooking.data.model.Booking;
import com.example.footballbooking.data.model.Pitch;
import com.example.footballbooking.utils.Constants;
import com.example.footballbooking.utils.Resource;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.WriteBatch;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * AdminRepository — Chứa tất cả logic CRUD dành riêng cho Admin.
 *
 * PHÂN QUYỀN:
 * Các thao tác này phải được bảo vệ bởi Firestore Security Rules
 * (chỉ user có role="admin" mới được phép).
 *
 * TÁCH BIỆT:
 * Admin thao tác qua AdminRepository — Customer không gọi class này.
 */
public class AdminRepository {

    private final FirebaseFirestore mDb;
    private final FirebaseStorage   mStorage;

    private ListenerRegistration allBookingsListener;
    private ListenerRegistration pendingBookingsListener;

    private static AdminRepository instance;

    private AdminRepository() {
        mDb      = FirebaseFirestore.getInstance();
        mStorage = FirebaseStorage.getInstance();
    }

    public static synchronized AdminRepository getInstance() {
        if (instance == null) instance = new AdminRepository();
        return instance;
    }

    // ============================================================
    // BOOKING MANAGEMENT
    // ============================================================

    /**
     * Lắng nghe realtime TẤT CẢ đơn đặt sân (Admin view).
     * Sort: mới nhất lên đầu, pending ưu tiên.
     */
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

    /**
     * Lắng nghe riêng đơn đang "pending" — tab ưu tiên của Admin.
     */
    public void getPendingBookings(MutableLiveData<Resource<List<Booking>>> result) {
        result.setValue(Resource.loading(null));
        detachPendingListener();

        pendingBookingsListener = mDb.collection(Constants.COL_BOOKINGS)
                .whereEqualTo("bookingStatus", Constants.STATUS_PENDING)
                .orderBy("createdAt", Query.Direction.ASCENDING) // FIFO — xử lý cũ trước
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

    /**
     * DUYỆT đơn đặt sân.
     * 1. bookingStatus → "approved"
     * 2. Cập nhật availability slot → "booked"
     * 3. Gửi FCM notification cho customer (qua Firestore trigger / Cloud Functions)
     */
    public void approveBooking(Booking booking,
                               MutableLiveData<Resource<Boolean>> result) {
        result.setValue(Resource.loading(null));

        WriteBatch batch = mDb.batch();

        // 1. Update booking status
        batch.update(
                mDb.collection(Constants.COL_BOOKINGS).document(booking.getBookingId()),
                "bookingStatus", Constants.STATUS_APPROVED
        );

        // 2. Lock availability slot
        String slotPath = Constants.COL_PITCHES + "/" + booking.getPitchId()
                + "/" + Constants.SUB_AVAILABILITY + "/" + booking.getBookingDate()
                + "/" + booking.getSlotId();
        Map<String, Object> slotUpdate = new HashMap<>();
        slotUpdate.put("status", "booked");
        slotUpdate.put("bookingId", booking.getBookingId());
        batch.set(mDb.document(slotPath), slotUpdate,
                com.google.firebase.firestore.SetOptions.merge());

        // 3. Tạo notification document → Cloud Function sẽ pick up và gửi FCM
        Map<String, Object> notifData = new HashMap<>();
        notifData.put("type", Constants.NOTIF_BOOKING_APPROVED);
        notifData.put("bookingId", booking.getBookingId());
        notifData.put("customerId", booking.getCustomerId());
        notifData.put("pitchName", booking.getPitchName());
        notifData.put("time", booking.getStartTime());
        notifData.put("processed", false);
        notifData.put("createdAt", com.google.firebase.firestore.FieldValue.serverTimestamp());
        batch.set(
                mDb.collection("fcm_queue").document(),
                notifData
        );

        batch.commit()
                .addOnSuccessListener(v -> result.setValue(Resource.success(true)))
                .addOnFailureListener(e ->
                        result.setValue(Resource.error("Duyệt đơn thất bại: " + e.getMessage(), false)));
    }

    /**
     * TỪ CHỐI đơn đặt sân kèm lý do.
     */
    public void rejectBooking(Booking booking, String reason,
                              MutableLiveData<Resource<Boolean>> result) {
        result.setValue(Resource.loading(null));

        WriteBatch batch = mDb.batch();

        // Update booking
        Map<String, Object> bookingUpdate = new HashMap<>();
        bookingUpdate.put("bookingStatus", Constants.STATUS_REJECTED);
        bookingUpdate.put("rejectionReason", reason != null ? reason : "");
        batch.update(
                mDb.collection(Constants.COL_BOOKINGS).document(booking.getBookingId()),
                bookingUpdate
        );

        // Trả slot về available
        String slotPath = Constants.COL_PITCHES + "/" + booking.getPitchId()
                + "/" + Constants.SUB_AVAILABILITY + "/" + booking.getBookingDate()
                + "/" + booking.getSlotId();
        batch.update(mDb.document(slotPath), "status", "available");

        // FCM queue
        Map<String, Object> notifData = new HashMap<>();
        notifData.put("type", Constants.NOTIF_BOOKING_REJECTED);
        notifData.put("bookingId", booking.getBookingId());
        notifData.put("customerId", booking.getCustomerId());
        notifData.put("pitchName", booking.getPitchName());
        notifData.put("reason", reason != null ? reason : "");
        notifData.put("processed", false);
        notifData.put("createdAt", com.google.firebase.firestore.FieldValue.serverTimestamp());
        batch.set(mDb.collection("fcm_queue").document(), notifData);

        batch.commit()
                .addOnSuccessListener(v -> result.setValue(Resource.success(true)))
                .addOnFailureListener(e ->
                        result.setValue(Resource.error("Từ chối thất bại: " + e.getMessage(), false)));
    }

    /**
     * CẬP NHẬT MATCH STATUS (Upcoming → Playing → Half_time → Finished).
     * Admin thay đổi trạng thái trận đấu realtime.
     */
    public void updateMatchStatus(String bookingId, String matchStatus,
                                  MutableLiveData<Resource<Boolean>> result) {
        mDb.collection(Constants.COL_BOOKINGS).document(bookingId)
                .update("matchStatus", matchStatus)
                .addOnSuccessListener(v -> result.setValue(Resource.success(true)))
                .addOnFailureListener(e ->
                        result.setValue(Resource.error(e.getMessage(), false)));
    }

    // ============================================================
    // PITCH MANAGEMENT
    // ============================================================

    /**
     * TẠO sân mới.
     */
    public void createPitch(Pitch pitch, MutableLiveData<Resource<Pitch>> result) {
        result.setValue(Resource.loading(null));

        String pitchId = mDb.collection(Constants.COL_PITCHES).document().getId();
        pitch.setPitchId(pitchId);

        mDb.collection(Constants.COL_PITCHES).document(pitchId)
                .set(pitch)
                .addOnSuccessListener(v -> result.setValue(Resource.success(pitch)))
                .addOnFailureListener(e ->
                        result.setValue(Resource.error("Tạo sân thất bại: " + e.getMessage(), null)));
    }

    /**
     * CẬP NHẬT thông tin sân.
     */
    public void updatePitch(Pitch pitch, MutableLiveData<Resource<Boolean>> result) {
        result.setValue(Resource.loading(null));

        mDb.collection(Constants.COL_PITCHES).document(pitch.getPitchId())
                .set(pitch)
                .addOnSuccessListener(v -> result.setValue(Resource.success(true)))
                .addOnFailureListener(e ->
                        result.setValue(Resource.error(e.getMessage(), false)));
    }

    /**
     * XÓA sân (soft delete: set status = "closed" thay vì xóa hẳn).
     * Tránh mất dữ liệu lịch sử booking đã có.
     */
    public void deletePitch(String pitchId, MutableLiveData<Resource<Boolean>> result) {
        mDb.collection(Constants.COL_PITCHES).document(pitchId)
                .update("status", Constants.PITCH_CLOSED)
                .addOnSuccessListener(v -> result.setValue(Resource.success(true)))
                .addOnFailureListener(e ->
                        result.setValue(Resource.error(e.getMessage(), false)));
    }

    /**
     * UPLOAD ảnh sân lên Firebase Storage.
     * Path: pitches/{pitchId}/{uuid}.jpg
     *
     * @param pitchId  ID sân cần upload ảnh
     * @param imageUri URI của ảnh từ Gallery
     * @param result   Trả về download URL sau khi upload xong
     */
    public void uploadPitchImage(String pitchId, android.net.Uri imageUri,
                                 MutableLiveData<Resource<String>> result) {
        result.setValue(Resource.loading(null));

        String fileName = UUID.randomUUID().toString() + ".jpg";
        StorageReference ref = mStorage.getReference()
                .child("pitches").child(pitchId).child(fileName);

        ref.putFile(imageUri)
                .addOnProgressListener(task -> {
                    double progress = (100.0 * task.getBytesTransferred()) / task.getTotalByteCount();
                    // Có thể emit progress nếu cần
                })
                .addOnSuccessListener(taskSnapshot ->
                        ref.getDownloadUrl()
                                .addOnSuccessListener(uri ->
                                        result.setValue(Resource.success(uri.toString())))
                                .addOnFailureListener(e ->
                                        result.setValue(Resource.error(e.getMessage(), null)))
                )
                .addOnFailureListener(e ->
                        result.setValue(Resource.error("Upload thất bại: " + e.getMessage(), null)));
    }

    // ============================================================
    // REPORT DATA
    // ============================================================

    /**
     * Lấy tất cả booking theo tháng để vẽ biểu đồ doanh thu.
     * Param: "2025-06" → format yyyy-MM
     */
    public void getBookingsByMonth(String yearMonth,
                                   MutableLiveData<Resource<List<Booking>>> result) {
        result.setValue(Resource.loading(null));

        // Tìm booking có bookingDate bắt đầu bằng "yyyy-MM"
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
    // CLEANUP
    // ============================================================

    public void detachAllBookingsListener() {
        if (allBookingsListener != null) {
            allBookingsListener.remove();
            allBookingsListener = null;
        }
    }

    public void detachPendingListener() {
        if (pendingBookingsListener != null) {
            pendingBookingsListener.remove();
            pendingBookingsListener = null;
        }
    }
}
