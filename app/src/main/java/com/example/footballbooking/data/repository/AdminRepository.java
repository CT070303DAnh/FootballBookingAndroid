package com.example.footballbooking.data.repository;

import androidx.lifecycle.MutableLiveData;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;

import com.example.footballbooking.data.model.Booking;
import com.example.footballbooking.data.model.Pitch;
import com.example.footballbooking.data.model.User;
import com.example.footballbooking.data.remote.RetrofitClient;
import com.example.footballbooking.data.remote.api.AdminUserApi;
import com.example.footballbooking.utils.Constants;
import com.example.footballbooking.utils.Resource;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * AdminRepository — Chứa logic thao tác dữ liệu dành riêng cho Admin.
 */
public class AdminRepository {

    private static final long ADMIN_LIST_LIMIT = 200;

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

    private long reportGeneration;

    public void getBookingsByMonth(String yearMonth,
                                   MutableLiveData<Resource<List<Booking>>> result) {
        final long generation = ++reportGeneration;
        result.setValue(Resource.loading(null));

        String startDate = yearMonth + "-01";
        String endDate   = yearMonth + "-31";

        mDb.collection(Constants.COL_BOOKINGS)
                .whereGreaterThanOrEqualTo("bookingDate", startDate)
                .whereLessThanOrEqualTo("bookingDate", endDate)
                .get()
                .addOnSuccessListener(snap -> {
                    if (generation == reportGeneration) result.setValue(Resource.success(snap.toObjects(Booking.class)));
                })
                .addOnFailureListener(e -> {
                    if (generation == reportGeneration) result.setValue(Resource.error(e.getMessage(), null));
                });
    }

    // ============================================================
    // USER MANAGEMENT
    // ============================================================

    public void getAllUsers(MutableLiveData<Resource<List<User>>> result) {
        result.setValue(Resource.loading(null));
        detachUsersListener();

        usersListener = mDb.collection(Constants.COL_USERS)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(ADMIN_LIST_LIMIT)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        result.setValue(Resource.error(error.getMessage(), null));
                        return;
                    }
                    List<User> users = snapshots != null
                            ? snapshots.toObjects(User.class) : null;
                    if (users != null) {
                        for (int i = 0; i < users.size(); i++) {
                            users.get(i).setUid(snapshots.getDocuments().get(i).getId());
                        }
                    }
                    result.setValue(Resource.success(users));
                });
    }

    public void updateUserStatus(String userId, String status, String reason,
                                 MutableLiveData<Resource<Boolean>> result) {
        if (userId == null || userId.isEmpty()) {
            result.setValue(Resource.error("User ID không hợp lệ", false));
            return;
        }
        if (!Constants.STATUS_ACTIVE.equals(status) && !Constants.STATUS_BLOCKED.equals(status)) {
            result.setValue(Resource.error("Trạng thái tài khoản không hợp lệ", false));
            return;
        }
        String adminUid = FirebaseAuth.getInstance().getCurrentUser() == null
                ? null : FirebaseAuth.getInstance().getCurrentUser().getUid();
        if (adminUid == null || adminUid.equals(userId)) {
            result.setValue(Resource.error("Không thể thay đổi trạng thái tài khoản Admin hiện tại", false));
            return;
        }
        String safeReason = reason == null ? "" : reason.trim();
        if (safeReason.length() > 500) {
            result.setValue(Resource.error("Lý do không được vượt quá 500 ký tự", false));
            return;
        }
        result.setValue(Resource.loading(null));
        DocumentReference ref = mDb.collection(Constants.COL_USERS).document(userId);
        mDb.runTransaction(tx -> {
            DocumentSnapshot target = tx.get(ref);
            if (!target.exists() || Constants.ROLE_ADMIN.equals(target.getString("role"))) {
                throw new IllegalStateException("Không thể khóa hoặc mở khóa tài khoản Admin.");
            }
            Map<String, Object> updates = new HashMap<>();
            updates.put("status", status);
            updates.put("statusReason", safeReason);
            updates.put("statusChangedBy", adminUid);
            updates.put("statusChangedAt", FieldValue.serverTimestamp());
            updates.put("updatedAt", FieldValue.serverTimestamp());
            tx.update(ref, updates);
            return true;
        }).addOnSuccessListener(value -> result.setValue(Resource.success(true)))
                .addOnFailureListener(e -> result.setValue(Resource.error(e.getMessage(), false)));
    }

    // ============================================================
    // USER CRUD — thực hiện qua Backend Firebase Admin SDK
    // ============================================================

    public void createUser(AdminUserApi.UserPayload payload,
                           MutableLiveData<Resource<Boolean>> result) {
        result.setValue(Resource.loading(null));
        withAdminToken(bearer -> RetrofitClient.getInstance().getAdminUserApi()
                .create(bearer, payload), result);
    }

    public void updateUser(String userId, AdminUserApi.UserPayload payload,
                           MutableLiveData<Resource<Boolean>> result) {
        if (userId == null || userId.trim().isEmpty()) {
            result.setValue(Resource.error("User ID không hợp lệ", false));
            return;
        }
        result.setValue(Resource.loading(null));
        withAdminToken(bearer -> RetrofitClient.getInstance().getAdminUserApi()
                .update(bearer, userId, payload), result);
    }

    public void deleteUser(String userId, MutableLiveData<Resource<Boolean>> result) {
        if (userId == null || userId.trim().isEmpty()) {
            result.setValue(Resource.error("User ID không hợp lệ", false));
            return;
        }
        result.setValue(Resource.loading(null));
        withAdminToken(bearer -> RetrofitClient.getInstance().getAdminUserApi()
                .delete(bearer, userId), result);
    }

    private interface AdminMutationCall {
        Call<AdminUserApi.MutationResponse> create(String bearer);
    }

    private void withAdminToken(AdminMutationCall request,
                                MutableLiveData<Resource<Boolean>> result) {
        FirebaseUser current = FirebaseAuth.getInstance().getCurrentUser();
        if (current == null) {
            result.setValue(Resource.error("Phiên đăng nhập đã hết hạn", false));
            return;
        }
        current.getIdToken(false)
                .addOnSuccessListener(token -> {
                    if (token == null || token.getToken() == null) {
                        result.setValue(Resource.error("Không lấy được Firebase ID token", false));
                        return;
                    }
                    request.create("Bearer " + token.getToken())
                            .enqueue(new Callback<AdminUserApi.MutationResponse>() {
                                @Override
                                public void onResponse(Call<AdminUserApi.MutationResponse> call,
                                                        Response<AdminUserApi.MutationResponse> response) {
                                    if (response.isSuccessful() && response.body() != null
                                            && response.body().ok) {
                                        result.setValue(Resource.success(true));
                                    } else if (response.code() == 401) {
                                        result.setValue(Resource.error(
                                                "Phiên Admin hết hạn. Vui lòng đăng nhập lại.", false));
                                    } else if (response.code() == 409) {
                                        result.setValue(Resource.error(
                                                "Dữ liệu bị trùng hoặc tài khoản chưa thể xóa.", false));
                                    } else {
                                        result.setValue(Resource.error(
                                                "Backend từ chối thao tác (HTTP " + response.code() + ")", false));
                                    }
                                }

                                @Override
                                public void onFailure(Call<AdminUserApi.MutationResponse> call,
                                                       Throwable t) {
                                    result.setValue(Resource.error(
                                            "Không kết nối được Backend: " + t.getMessage(), false));
                                }
                            });
                })
                .addOnFailureListener(e -> result.setValue(
                        Resource.error("Không lấy được Firebase ID token", false)));
    }

    public void approveOwner(String userId, MutableLiveData<Resource<Boolean>> result) {
        if (userId == null || userId.isEmpty()) {
            result.setValue(Resource.error("User ID không hợp lệ", false));
            return;
        }
        result.setValue(Resource.loading(null));
        com.google.firebase.firestore.DocumentReference ref = mDb.collection(Constants.COL_USERS).document(userId);
        mDb.runTransaction(tx -> {
            DocumentSnapshot profile = tx.get(ref);
            if (!profile.exists() || !Constants.ROLE_OWNER_PENDING.equals(profile.getString("role"))
                    || !Constants.STATUS_ACTIVE.equals(profile.getString("status"))) {
                throw new IllegalStateException("Tài khoản đã thay đổi hoặc đang bị khóa. Vui lòng tải lại danh sách.");
            }
            tx.update(ref, "role", Constants.ROLE_OWNER,
                    "ownerApplicationStatus", Constants.OWNER_APPLICATION_APPROVED,
                    "ownerDecisionReason", "",
                    "ownerReviewedBy", currentAdminUid(),
                    "ownerReviewedAt", FieldValue.serverTimestamp(),
                    "updatedAt", com.google.firebase.firestore.FieldValue.serverTimestamp());
            return true;
        }).addOnSuccessListener(value -> result.setValue(Resource.success(value)))
          .addOnFailureListener(e -> result.setValue(Resource.error(e.getMessage(), false)));
    }

    public void rejectOwner(String userId, String reason, MutableLiveData<Resource<Boolean>> result) {
        if (userId == null || userId.isEmpty()) {
            result.setValue(Resource.error("User ID không hợp lệ", false));
            return;
        }
        String safeReason = reason == null ? "" : reason.trim();
        if (safeReason.isEmpty()) {
            result.setValue(Resource.error("Cần nhập lý do từ chối", false));
            return;
        }
        if (safeReason.length() > 500) {
            result.setValue(Resource.error("Lý do không được vượt quá 500 ký tự", false));
            return;
        }
        result.setValue(Resource.loading(null));
        DocumentReference ref = mDb.collection(Constants.COL_USERS).document(userId);
        mDb.runTransaction(tx -> {
            DocumentSnapshot profile = tx.get(ref);
            if (!profile.exists() || !Constants.ROLE_OWNER_PENDING.equals(profile.getString("role"))
                    || !Constants.STATUS_ACTIVE.equals(profile.getString("status"))) {
                throw new IllegalStateException("Tài khoản đã thay đổi hoặc đang bị khóa. Vui lòng tải lại danh sách.");
            }
            tx.update(ref, "role", Constants.ROLE_CUSTOMER,
                    "ownerApplicationStatus", Constants.OWNER_APPLICATION_REJECTED,
                    "ownerDecisionReason", safeReason,
                    "ownerReviewedBy", currentAdminUid(),
                    "ownerReviewedAt", FieldValue.serverTimestamp(),
                    "updatedAt", FieldValue.serverTimestamp());
            return true;
        }).addOnSuccessListener(value -> result.setValue(Resource.success(value)))
          .addOnFailureListener(e -> result.setValue(Resource.error(e.getMessage(), false)));
    }

    private String currentAdminUid() {
        return FirebaseAuth.getInstance().getCurrentUser() == null
                ? "" : FirebaseAuth.getInstance().getCurrentUser().getUid();
    }

    public void getBookingsForUser(String userId, MutableLiveData<Resource<List<Booking>>> result) {
        if (userId == null || userId.isEmpty()) {
            result.setValue(Resource.error("User ID không hợp lệ", null));
            return;
        }
        result.setValue(Resource.loading(null));
        Task<QuerySnapshot> customerBookings = mDb.collection(Constants.COL_BOOKINGS)
                .whereEqualTo("customerId", userId)
                .limit(50)
                .get();
        Task<QuerySnapshot> ownerBookings = mDb.collection(Constants.COL_BOOKINGS)
                .whereEqualTo("pitchOwnerId", userId)
                .limit(50)
                .get();
        Tasks.whenAllSuccess(customerBookings, ownerBookings)
                .addOnSuccessListener(results -> {
                    Map<String, Booking> unique = new LinkedHashMap<>();
                    for (Object value : results) {
                        QuerySnapshot snapshot = (QuerySnapshot) value;
                        for (DocumentSnapshot document : snapshot.getDocuments()) {
                            Booking booking = document.toObject(Booking.class);
                            if (booking == null) continue;
                            if (booking.getBookingId() == null || booking.getBookingId().isEmpty()) {
                                booking.setBookingId(document.getId());
                            }
                            unique.put(booking.getBookingId(), booking);
                        }
                    }
                    List<Booking> bookings = new java.util.ArrayList<>(unique.values());
                    bookings.sort((a, b) -> {
                        if (a.getCreatedAt() == null || b.getCreatedAt() == null) return 0;
                        return b.getCreatedAt().compareTo(a.getCreatedAt());
                    });
                    if (bookings.size() > 50) {
                        bookings = new java.util.ArrayList<>(bookings.subList(0, 50));
                    }
                    result.setValue(Resource.success(bookings));
                })
                .addOnFailureListener(e -> result.setValue(Resource.error(e.getMessage(), null)));
    }

    // ============================================================
    // PITCH MODERATION
    // ============================================================

    public void getAllPitchesForModeration(MutableLiveData<Resource<List<Pitch>>> result) {
        result.setValue(Resource.loading(null));
        detachPitchesListener();

        pitchesListener = mDb.collection(Constants.COL_PITCHES)
                .limit(ADMIN_LIST_LIMIT)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        result.setValue(Resource.error(error.getMessage(), null));
                        return;
                    }
                    List<Pitch> pitches = snapshots != null
                            ? snapshots.toObjects(Pitch.class) : null;
                    if (pitches != null && snapshots != null) {
                        for (int i = 0; i < pitches.size(); i++) {
                            if (pitches.get(i).getPitchId() == null
                                    || pitches.get(i).getPitchId().isEmpty()) {
                                pitches.get(i).setPitchId(snapshots.getDocuments().get(i).getId());
                            }
                        }
                        pitches.sort((left, right) -> {
                            if (left.getCreatedAt() == null && right.getCreatedAt() == null) return 0;
                            if (left.getCreatedAt() == null) return 1;
                            if (right.getCreatedAt() == null) return -1;
                            return right.getCreatedAt().compareTo(left.getCreatedAt());
                        });
                    }
                    result.setValue(Resource.success(pitches));
                });
    }

    public void moderatePitch(String pitchId, String moderationStatus, String reason,
                              MutableLiveData<Resource<Boolean>> result) {
        if (pitchId == null || pitchId.trim().isEmpty()) {
            result.setValue(Resource.error("Pitch ID không hợp lệ", false));
            return;
        }
        if (!Constants.PITCH_MODERATION_PENDING.equals(moderationStatus)
                && !Constants.PITCH_MODERATION_APPROVED.equals(moderationStatus)
                && !Constants.PITCH_MODERATION_REJECTED.equals(moderationStatus)
                && !Constants.PITCH_MODERATION_SUSPENDED.equals(moderationStatus)
                && !Constants.PITCH_MODERATION_ARCHIVED.equals(moderationStatus)) {
            result.setValue(Resource.error("Trạng thái kiểm duyệt không hợp lệ", false));
            return;
        }
        String safeReason = reason == null ? "" : reason.trim();
        boolean requiresReason = Constants.PITCH_MODERATION_REJECTED.equals(moderationStatus)
                || Constants.PITCH_MODERATION_SUSPENDED.equals(moderationStatus)
                || Constants.PITCH_MODERATION_ARCHIVED.equals(moderationStatus);
        if (requiresReason && safeReason.isEmpty()) {
            result.setValue(Resource.error("Vui lòng nhập lý do xử lý sân", false));
            return;
        }
        if (safeReason.length() > 500) {
            result.setValue(Resource.error("Lý do không được vượt quá 500 ký tự", false));
            return;
        }
        result.setValue(Resource.loading(null));
        DocumentReference ref = mDb.collection(Constants.COL_PITCHES).document(pitchId);
        mDb.runTransaction(tx -> {
            DocumentSnapshot snapshot = tx.get(ref);
            if (!snapshot.exists()) throw new IllegalStateException("Không tìm thấy sân bóng");
            Map<String, Object> updates = new HashMap<>();
            updates.put("moderationStatus", moderationStatus);
            updates.put("moderationReason", safeReason);
            updates.put("moderationBy", currentAdminUid());
            updates.put("moderationAt", FieldValue.serverTimestamp());
            updates.put("updatedAt", FieldValue.serverTimestamp());
            tx.update(ref, updates);
            return true;
        }).addOnSuccessListener(value -> result.setValue(Resource.success(true)))
                .addOnFailureListener(e -> result.setValue(Resource.error(e.getMessage(), false)));
    }

    public void updatePitchDetails(Pitch pitch, MutableLiveData<Resource<Boolean>> result) {
        if (pitch == null || pitch.getPitchId() == null || pitch.getPitchId().trim().isEmpty()) {
            result.setValue(Resource.error("Dữ liệu sân không hợp lệ", false));
            return;
        }
        if (pitch.getName() == null || pitch.getName().trim().isEmpty()
                || pitch.getName().trim().length() > 120
                || pitch.getAddress() == null || pitch.getAddress().trim().isEmpty()
                || pitch.getAddress().trim().length() > 300
                || (pitch.getDescription() != null && pitch.getDescription().length() > 2000)
                || pitch.getBasePrice() < 0) {
            result.setValue(Resource.error("Thông tin sân không hợp lệ hoặc quá dài", false));
            return;
        }
        if (!Constants.PITCH_TYPE_5.equals(pitch.getType())
                && !Constants.PITCH_TYPE_7.equals(pitch.getType())
                && !Constants.PITCH_TYPE_11.equals(pitch.getType())) {
            result.setValue(Resource.error("Loại sân không hợp lệ", false));
            return;
        }
        result.setValue(Resource.loading(null));
        Map<String, Object> updates = new HashMap<>();
        updates.put("name", pitch.getName().trim());
        updates.put("description", pitch.getDescription() == null ? "" : pitch.getDescription().trim());
        updates.put("type", pitch.getType());
        updates.put("address", pitch.getAddress().trim());
        updates.put("location", pitch.getLocation());
        updates.put("imageUrls", pitch.getImageUrls() == null ? java.util.Collections.emptyList() : pitch.getImageUrls());
        updates.put("amenities", pitch.getAmenities() == null ? java.util.Collections.emptyList() : pitch.getAmenities());
        updates.put("basePrice", pitch.getBasePrice());
        updates.put("updatedAt", FieldValue.serverTimestamp());
        mDb.collection(Constants.COL_PITCHES).document(pitch.getPitchId())
                .update(updates)
                .addOnSuccessListener(value -> result.setValue(Resource.success(true)))
                .addOnFailureListener(e -> result.setValue(Resource.error(e.getMessage(), false)));
    }

    public void updatePitchStatus(String pitchId, String status, MutableLiveData<Resource<Boolean>> result) {
        if (pitchId == null) {
            result.setValue(Resource.error("Pitch ID không hợp lệ", false));
            return;
        }
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
                .limit(ADMIN_LIST_LIMIT)
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
