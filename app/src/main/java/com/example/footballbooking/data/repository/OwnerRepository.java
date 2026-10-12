package com.example.footballbooking.data.repository;

import androidx.lifecycle.MutableLiveData;

import com.example.footballbooking.data.model.Booking;
import com.example.footballbooking.data.model.Pitch;
import com.example.footballbooking.utils.Constants;
import com.example.footballbooking.utils.Resource;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
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
        pitch.setModerationStatus(Constants.PITCH_MODERATION_PENDING);
        pitch.setModerationReason("");
        pitch.setModerationBy("");
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
        if (pitch == null || pitch.getPitchId() == null) {
            result.setValue(Resource.error("Dữ liệu sân không hợp lệ", false));
            return;
        }
        if (!ownerId.equals(pitch.getOwnerId())) {
            result.setValue(Resource.error("Không có quyền chỉnh sửa sân này", false));
            return;
        }
        result.setValue(Resource.loading(null));
        // Chỉ gửi các trường owner được phép sửa; không ghi đè moderationStatus
        // hoặc các trường rating/chính sách bằng dữ liệu cũ trong client.
        Map<String, Object> updates = new HashMap<>();
        updates.put("name", pitch.getName());
        updates.put("description", pitch.getDescription() == null ? "" : pitch.getDescription());
        updates.put("type", pitch.getType());
        updates.put("status", pitch.getStatus());
        updates.put("address", pitch.getAddress());
        updates.put("location", pitch.getLocation());
        updates.put("imageUrls", pitch.getImageUrls() == null ? java.util.Collections.emptyList() : pitch.getImageUrls());
        updates.put("amenities", pitch.getAmenities() == null ? java.util.Collections.emptyList() : pitch.getAmenities());
        updates.put("basePrice", pitch.getBasePrice());
        updates.put("ownerName", pitch.getOwnerName() == null ? "" : pitch.getOwnerName());
        updates.put("ownerPhone", pitch.getOwnerPhone() == null ? "" : pitch.getOwnerPhone());
        updates.put("updatedAt", FieldValue.serverTimestamp());
        mDb.collection(Constants.COL_PITCHES).document(pitch.getPitchId())
                .update(updates)
                .addOnSuccessListener(v -> result.setValue(Resource.success(true)))
                .addOnFailureListener(e -> result.setValue(
                        Resource.error(e.getMessage(), false)));
    }

    /**
     * Đóng sân (soft delete) — chỉ chủ sân mới được phép.
     */
    public void closeMyPitch(String pitchId, String ownerId,
                             MutableLiveData<Resource<Boolean>> result) {
        if (pitchId == null) {
            result.setValue(Resource.error("ID sân không hợp lệ", false));
            return;
        }
        // Verify ownership trước
        mDb.collection(Constants.COL_PITCHES).document(pitchId).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists() || !ownerId.equals(doc.getString("ownerId"))) {
                        result.setValue(Resource.error("Không có quyền thao tác", false));
                        return;
                    }
                    doc.getReference().update("status", Constants.PITCH_CLOSED,
                                    "updatedAt", FieldValue.serverTimestamp())
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
        if (pitchId == null) {
            result.setValue(Resource.error("ID sân không hợp lệ", null));
            return;
        }
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
                            update.put("updatedAt", FieldValue.serverTimestamp());
                            mDb.collection(Constants.COL_PITCHES).document(pitchId)
                                    .update(update)
                                    .addOnSuccessListener(v -> result.setValue(
                                            Resource.success(uri.toString())))
                                    .addOnFailureListener(e -> result.setValue(
                                            Resource.error("Không thể cập nhật ảnh sân", null)));
                        })
                        .addOnFailureListener(e -> result.setValue(
                                Resource.error("Không thể lấy URL ảnh", null))))
                .addOnFailureListener(e -> result.setValue(
                        Resource.error("Upload ảnh thất bại", null)));
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
        if (booking == null || booking.getBookingId() == null || booking.getPitchId() == null || booking.getBookingDate() == null) {
            result.setValue(Resource.error("Dữ liệu đơn hàng không hợp lệ", false));
            return;
        }
        if (!ownerId.equals(booking.getPitchOwnerId())) {
            result.setValue(Resource.error("Không có quyền duyệt đơn này", false));
            return;
        }
        result.setValue(Resource.loading(null));

        BookingRepository.getInstance().transitionBooking(booking.getBookingId(), Constants.STATUS_APPROVED, null)
                .addOnSuccessListener(v -> result.setValue(Resource.success(true)))
                .addOnFailureListener(e -> result.setValue(Resource.error("Duyệt thất bại: " + e.getMessage(), false)));
    }

    /**
     * TỪ CHỐI đơn đặt sân.
     */
    public void rejectBooking(Booking booking, String reason, String ownerId,
                              MutableLiveData<Resource<Boolean>> result) {
        if (booking == null || booking.getBookingId() == null) {
            result.setValue(Resource.error("Dữ liệu đơn hàng không hợp lệ", false));
            return;
        }
        if (!ownerId.equals(booking.getPitchOwnerId())) {
            result.setValue(Resource.error("Không có quyền từ chối đơn này", false));
            return;
        }
        result.setValue(Resource.loading(null));

        BookingRepository.getInstance().transitionBooking(booking.getBookingId(), Constants.STATUS_REJECTED, reason)
                .addOnSuccessListener(v -> result.setValue(Resource.success(true)))
                .addOnFailureListener(e -> result.setValue(Resource.error("Từ chối thất bại: " + e.getMessage(), false)));
    }

    /**
     * Cập nhật match status (Chủ sân cũng có thể làm điều này).
     */
    public void updateMatchStatus(String bookingId, String matchStatus,
                                  MutableLiveData<Resource<Boolean>> result) {
        if (bookingId == null) {
            result.setValue(Resource.error("ID đơn hàng không hợp lệ", false));
            return;
        }
        mDb.collection(Constants.COL_BOOKINGS).document(bookingId)
                .update("matchStatus", matchStatus)
                .addOnSuccessListener(v -> result.setValue(Resource.success(true)))
                .addOnFailureListener(e -> result.setValue(Resource.error(e.getMessage(), false)));
    }

    /** Xác nhận đã nhận tiền mặt tại sân sau khi khung giờ đã kết thúc. */
    public void confirmCashPayment(Booking booking, String ownerId,
                                   MutableLiveData<Resource<Boolean>> result) {
        if (booking == null || booking.getBookingId() == null) {
            result.setValue(Resource.error("Dữ liệu đơn hàng không hợp lệ", false));
            return;
        }
        if (!ownerId.equals(booking.getPitchOwnerId())) {
            result.setValue(Resource.error("Không có quyền thao tác đơn này", false));
            return;
        }
        if (!Constants.METHOD_CASH.equals(booking.getPaymentMethod())
                || !Constants.PAYMENT_UNPAID.equals(booking.getPaymentStatus())) {
            result.setValue(Resource.error("Đơn này không phải thanh toán tiền mặt chưa xác nhận", false));
            return;
        }
        result.setValue(Resource.loading(null));
        // Payment fields are protected by Firestore Rules. Settlement must be
        // performed by the trusted backend, not by a client-side update.
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || !ownerId.equals(user.getUid())) {
            result.setValue(Resource.error("Phiên đăng nhập chủ sân không hợp lệ", false));
            return;
        }
        user.getIdToken(false).addOnCompleteListener(tokenTask -> {
            if (!tokenTask.isSuccessful() || tokenTask.getResult() == null
                    || tokenTask.getResult().getToken() == null) {
                result.setValue(Resource.error("Không xác thực được chủ sân", false));
                return;
            }
            com.example.footballbooking.data.remote.api.PaymentApi api =
                    com.example.footballbooking.data.remote.RetrofitClient.getInstance().getPaymentApi();
            api.confirmCashPayment(
                    "Bearer " + tokenTask.getResult().getToken(),
                    new com.example.footballbooking.data.remote.api.PaymentApi.CashConfirmRequest(
                            booking.getBookingId()))
                    .enqueue(new retrofit2.Callback<com.example.footballbooking.data.remote.api.PaymentApi.CashConfirmation>() {
                        @Override
                        public void onResponse(retrofit2.Call<com.example.footballbooking.data.remote.api.PaymentApi.CashConfirmation> call,
                                               retrofit2.Response<com.example.footballbooking.data.remote.api.PaymentApi.CashConfirmation> response) {
                            if (response.isSuccessful() && response.body() != null && response.body().ok) {
                                result.setValue(Resource.success(true));
                            } else {
                                result.setValue(Resource.error(
                                        "Xác nhận thanh toán thất bại (HTTP " + response.code() + ")", false));
                            }
                        }

                        @Override
                        public void onFailure(retrofit2.Call<com.example.footballbooking.data.remote.api.PaymentApi.CashConfirmation> call,
                                              Throwable error) {
                            result.setValue(Resource.error("Không thể kết nối máy chủ thanh toán", false));
                        }
                    });
        });
    }

    /**
     * Lấy booking theo tháng để tính doanh thu — chỉ của sân owner.
     */
    private long reportGeneration;

    public void getMyBookingsByMonth(String ownerId, String yearMonth,
                                     MutableLiveData<Resource<List<Booking>>> result) {
        final long generation = ++reportGeneration;
        result.setValue(Resource.loading(null));
        mDb.collection(Constants.COL_BOOKINGS)
                .whereEqualTo("pitchOwnerId", ownerId)
                .whereGreaterThanOrEqualTo("bookingDate", yearMonth + "-01")
                .whereLessThanOrEqualTo("bookingDate", yearMonth + "-31")
                .get()
                .addOnSuccessListener(snap -> {
                    if (generation == reportGeneration) result.setValue(Resource.success(snap.toObjects(Booking.class)));
                })
                .addOnFailureListener(e -> {
                    if (generation == reportGeneration) result.setValue(Resource.error(e.getMessage(), null));
                });
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
