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
 * OwnerRepository — Nghiệp vụ dành riêng cho Chủ sân (Owner).
 *
 * NGUYÊN TẮC PHÂN QUYỀN:
 * - Owner chỉ THẤY và THAO TÁC sân có ownerId == uid của mình.
 * - Firestore Security Rules sẽ enforce điều này ở server-side.
 * - Client-side: tất cả query đều có .whereEqualTo("ownerId", uid).
 *
 * KHÁC BIỆT SO VỚI AdminRepository:
 * - Admin: xem TẤT CẢ sân / đơn.
 * - Owner: chỉ xem SÂN CỦA MÌNH và đơn đặt TRÊN SÂN ĐÓ.
 */
public class OwnerRepository {

    private final FirebaseFirestore mDb;
    private final FirebaseStorage   mStorage;

    private ListenerRegistration pitchListener;
    private ListenerRegistration bookingListener;

    private static OwnerRepository instance;

    private OwnerRepository() {
        mDb      = FirebaseFirestore.getInstance();
        mStorage = FirebaseStorage.getInstance();
    }

    public static synchronized OwnerRepository getInstance() {
        if (instance == null) instance = new OwnerRepository();
        return instance;
    }

    // ============================================================
    // PITCH MANAGEMENT — chỉ sân của owner này
    // ============================================================

    /**
     * Lắng nghe realtime sân của owner hiện tại.
     * @param ownerId  UID của owner đang đăng nhập
     */
    public void getMyPitches(String ownerId,
                             MutableLiveData<Resource<List<Pitch>>> result) {
        result.setValue(Resource.loading(null));
        detachPitchListener();

        pitchListener = mDb.collection(Constants.COL_PITCHES)
                .whereEqualTo("ownerId", ownerId)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        result.setValue(Resource.error(error.getMessage(), null));
                        return;
                    }
                    result.setValue(Resource.success(
                            snapshots != null ? snapshots.toObjects(Pitch.class) : null));
                });
    }

    /**
     * Tạo sân mới — gắn ownerId + ownerName tự động.
     */
    public void createMyPitch(Pitch pitch, String ownerId, String ownerName, String ownerPhone,
                              MutableLiveData<Resource<Pitch>> result) {
        result.setValue(Resource.loading(null));

        String pitchId = mDb.collection(Constants.COL_PITCHES).document().getId();
        pitch.setPitchId(pitchId);
        pitch.setOwnerId(ownerId);
        pitch.setOwnerName(ownerName);
        pitch.setOwnerPhone(ownerPhone);
        pitch.setStatus(Constants.PITCH_AVAILABLE);
        pitch.setRating(0f);
        pitch.setTotalReviews(0);

        mDb.collection(Constants.COL_PITCHES).document(pitchId).set(pitch)
                .addOnSuccessListener(v -> result.setValue(Resource.success(pitch)))
                .addOnFailureListener(e -> result.setValue(
                        Resource.error("Tạo sân thất bại: " + e.getMessage(), null)));
    }

    /**
     * Cập nhật thông tin sân — kiểm tra ownerId trước khi cho phép.
     */
    public void updateMyPitch(Pitch pitch, String ownerId,
                              MutableLiveData<Resource<Boolean>> result) {
        if (!ownerId.equals(pitch.getOwnerId())) {
            result.setValue(Resource.error("Không có quyền chỉnh sửa sân này", false));
            return;
        }
        result.setValue(Resource.loading(null));
        mDb.collection(Constants.COL_PITCHES).document(pitch.getPitchId())
                .set(pitch)
                .addOnSuccessListener(v -> result.setValue(Resource.success(true)))
                .addOnFailureListener(e -> result.setValue(
                        Resource.error(e.getMessage(), false)));
    }

    /**
     * Đóng sân (soft delete) — chỉ chủ sân mới được phép.
     */
    public void closeMyPitch(String pitchId, String ownerId,
                             MutableLiveData<Resource<Boolean>> result) {
        // Verify ownership trước
        mDb.collection(Constants.COL_PITCHES).document(pitchId).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists() || !ownerId.equals(doc.getString("ownerId"))) {
                        result.setValue(Resource.error("Không có quyền thao tác", false));
                        return;
                    }
                    doc.getReference().update("status", Constants.PITCH_CLOSED)
                            .addOnSuccessListener(v -> result.setValue(Resource.success(true)))
                            .addOnFailureListener(e -> result.setValue(
                                    Resource.error(e.getMessage(), false)));
                })
                .addOnFailureListener(e -> result.setValue(
                        Resource.error(e.getMessage(), false)));
    }

    /**
     * Upload ảnh sân — giống AdminRepository.
     */
    public void uploadPitchImage(String pitchId, android.net.Uri imageUri,
                                 MutableLiveData<Resource<String>> result) {
        result.setValue(Resource.loading(null));
        String fileName = UUID.randomUUID() + ".jpg";
        StorageReference ref = mStorage.getReference()
                .child("pitches").child(pitchId).child(fileName);

        ref.putFile(imageUri)
                .addOnSuccessListener(task -> ref.getDownloadUrl()
                        .addOnSuccessListener(uri -> {
                            // Append URL vào imageUrls của pitch
                            Map<String, Object> update = new HashMap<>();
                            update.put("imageUrls",
                                    com.google.firebase.firestore.FieldValue.arrayUnion(uri.toString()));
                            mDb.collection(Constants.COL_PITCHES).document(pitchId)
                                    .update(update);
                            result.setValue(Resource.success(uri.toString()));
                        })
                        .addOnFailureListener(e -> result.setValue(
                                Resource.error(e.getMessage(), null))))
                .addOnFailureListener(e -> result.setValue(
                        Resource.error("Upload thất bại: " + e.getMessage(), null)));
    }

    // ============================================================
    // BOOKING MANAGEMENT — đơn trên sân của owner
    // ============================================================

    /**
     * Lắng nghe realtime TẤT CẢ đơn đặt sân trên các sân của owner.
     * Query: bookings WHERE pitchOwnerId == ownerId (cần denormalize field này).
     */
    public void getBookingsForMyPitches(String ownerId,
                                        MutableLiveData<Resource<List<Booking>>> result) {
        result.setValue(Resource.loading(null));
        detachBookingListener();

        bookingListener = mDb.collection(Constants.COL_BOOKINGS)
                .whereEqualTo("pitchOwnerId", ownerId)  // field denormalized trong Booking
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        result.setValue(Resource.error(error.getMessage(), null));
                        return;
                    }
                    result.setValue(Resource.success(
                            snapshots != null ? snapshots.toObjects(Booking.class) : null));
                });
    }

    /**
     * DUYỆT đơn đặt sân — giống Admin nhưng owner chỉ duyệt sân của mình.
     */
    public void approveBooking(Booking booking, String ownerId,
                               MutableLiveData<Resource<Boolean>> result) {
        if (!ownerId.equals(booking.getPitchOwnerId())) {
            result.setValue(Resource.error("Không có quyền duyệt đơn này", false));
            return;
        }
        result.setValue(Resource.loading(null));

        WriteBatch batch = mDb.batch();
        batch.update(
                mDb.collection(Constants.COL_BOOKINGS).document(booking.getBookingId()),
                "bookingStatus", Constants.STATUS_APPROVED);

        // Lock slot
        Map<String, Object> slotUpdate = new HashMap<>();
        slotUpdate.put("slots." + booking.getSlotId() + ".status", "booked");
        slotUpdate.put("slots." + booking.getSlotId() + ".bookingId", booking.getBookingId());
        batch.set(
                mDb.collection(Constants.COL_PITCHES)
                   .document(booking.getPitchId())
                   .collection(Constants.SUB_AVAILABILITY)
                   .document(booking.getBookingDate()),
                slotUpdate,
                com.google.firebase.firestore.SetOptions.merge()
        );

        // FCM queue
        Map<String, Object> fcm = new HashMap<>();
        fcm.put("type", Constants.NOTIF_BOOKING_APPROVED);
        fcm.put("bookingId", booking.getBookingId());
        fcm.put("customerId", booking.getCustomerId());
        fcm.put("pitchName", booking.getPitchName());
        fcm.put("time", booking.getStartTime());
        fcm.put("processed", false);
        fcm.put("createdAt", com.google.firebase.firestore.FieldValue.serverTimestamp());
        batch.set(mDb.collection("fcm_queue").document(), fcm);

        batch.commit()
                .addOnSuccessListener(v -> result.setValue(Resource.success(true)))
                .addOnFailureListener(e -> result.setValue(
                        Resource.error("Duyệt thất bại: " + e.getMessage(), false)));
    }

    /**
     * TỪ CHỐI đơn đặt sân.
     */
    public void rejectBooking(Booking booking, String reason, String ownerId,
                              MutableLiveData<Resource<Boolean>> result) {
        if (!ownerId.equals(booking.getPitchOwnerId())) {
            result.setValue(Resource.error("Không có quyền từ chối đơn này", false));
            return;
        }
        result.setValue(Resource.loading(null));

        Map<String, Object> update = new HashMap<>();
        update.put("bookingStatus", Constants.STATUS_REJECTED);
        update.put("rejectionReason", reason != null ? reason : "");

        mDb.collection(Constants.COL_BOOKINGS).document(booking.getBookingId())
                .update(update)
                .addOnSuccessListener(v -> result.setValue(Resource.success(true)))
                .addOnFailureListener(e -> result.setValue(
                        Resource.error(e.getMessage(), false)));
    }

    /**
     * Cập nhật match status (Chủ sân cũng có thể làm điều này).
     */
    public void updateMatchStatus(String bookingId, String matchStatus,
                                  MutableLiveData<Resource<Boolean>> result) {
        mDb.collection(Constants.COL_BOOKINGS).document(bookingId)
                .update("matchStatus", matchStatus)
                .addOnSuccessListener(v -> result.setValue(Resource.success(true)))
                .addOnFailureListener(e -> result.setValue(Resource.error(e.getMessage(), false)));
    }

    /**
     * Lấy booking theo tháng để tính doanh thu — chỉ của sân owner.
     */
    public void getMyBookingsByMonth(String ownerId, String yearMonth,
                                     MutableLiveData<Resource<List<Booking>>> result) {
        result.setValue(Resource.loading(null));
        mDb.collection(Constants.COL_BOOKINGS)
                .whereEqualTo("pitchOwnerId", ownerId)
                .whereEqualTo("bookingStatus", Constants.STATUS_APPROVED)
                .whereGreaterThanOrEqualTo("bookingDate", yearMonth + "-01")
                .whereLessThanOrEqualTo("bookingDate", yearMonth + "-31")
                .get()
                .addOnSuccessListener(snap -> result.setValue(
                        Resource.success(snap.toObjects(Booking.class))))
                .addOnFailureListener(e -> result.setValue(
                        Resource.error(e.getMessage(), null)));
    }

    // ============================================================
    // CLEANUP
    // ============================================================

    public void detachPitchListener() {
        if (pitchListener != null) { pitchListener.remove(); pitchListener = null; }
    }

    public void detachBookingListener() {
        if (bookingListener != null) { bookingListener.remove(); bookingListener = null; }
    }

    public void detachAll() {
        detachPitchListener();
        detachBookingListener();
    }
}
