package com.example.footballbooking.data.repository;

import androidx.lifecycle.MutableLiveData;

import com.example.footballbooking.data.model.Review;
import com.example.footballbooking.data.remote.RetrofitClient;
import com.example.footballbooking.data.remote.api.ReviewApi;
import com.example.footballbooking.utils.Constants;
import com.example.footballbooking.utils.Resource;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Đọc review realtime và gửi review mới qua backend để cập nhật rating sân. */
public class ReviewRepository {

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private ListenerRegistration reviewsListener;

    public static ReviewRepository createScoped() {
        return new ReviewRepository();
    }

    public void getReviewsForPitch(String pitchId,
                                   MutableLiveData<Resource<List<Review>>> result) {
        result.setValue(Resource.loading(null));
        detachReviewsListener();
        if (pitchId == null || pitchId.trim().isEmpty()) {
            result.setValue(Resource.error("Mã sân không hợp lệ", null));
            return;
        }

        reviewsListener = db.collection(Constants.COL_REVIEWS)
                .whereEqualTo("pitchId", pitchId)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        result.setValue(Resource.error("Không tải được đánh giá: " + error.getMessage(), null));
                        return;
                    }
                    List<Review> reviews = snapshots == null
                            ? new ArrayList<>() : snapshots.toObjects(Review.class);
                    reviews.sort((a, b) -> {
                        if (a.getCreatedAt() == null) return 1;
                        if (b.getCreatedAt() == null) return -1;
                        return b.getCreatedAt().compareTo(a.getCreatedAt());
                    });
                    result.setValue(Resource.success(reviews));
                });
    }

    public void getReviewedBookingIds(String customerId,
                                      MutableLiveData<Resource<Set<String>>> result) {
        result.setValue(Resource.loading(null));
        if (customerId == null || customerId.trim().isEmpty()) {
            result.setValue(Resource.success(new HashSet<>()));
            return;
        }
        db.collection(Constants.COL_REVIEWS)
                .whereEqualTo("customerId", customerId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    Set<String> ids = new HashSet<>();
                    snapshot.getDocuments().forEach(document -> {
                        String bookingId = document.getString("bookingId");
                        if (bookingId != null && !bookingId.trim().isEmpty()) ids.add(bookingId);
                    });
                    result.setValue(Resource.success(ids));
                })
                .addOnFailureListener(error -> result.setValue(
                        Resource.error("Không tải được trạng thái đánh giá", null)));
    }

    public void createReview(String bookingId, float rating, String comment,
                             MutableLiveData<Resource<Boolean>> result) {
        result.setValue(Resource.loading(null));
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            result.setValue(Resource.error("Vui lòng đăng nhập để đánh giá", false));
            return;
        }

        user.getIdToken(false).addOnCompleteListener(tokenTask -> {
            if (!tokenTask.isSuccessful() || tokenTask.getResult() == null) {
                result.setValue(Resource.error("Không xác thực được tài khoản", false));
                return;
            }
            String token = tokenTask.getResult().getToken();
            if (token == null || token.isEmpty()) {
                result.setValue(Resource.error("Phiên đăng nhập không hợp lệ", false));
                return;
            }

            ReviewApi.CreateRequest request = new ReviewApi.CreateRequest(
                    bookingId, rating, comment == null ? "" : comment.trim());
            RetrofitClient.getInstance().getReviewApi()
                    .create("Bearer " + token, request)
                    .enqueue(new Callback<ReviewApi.CreateResponse>() {
                        @Override
                        public void onResponse(Call<ReviewApi.CreateResponse> call,
                                               Response<ReviewApi.CreateResponse> response) {
                            if (response.isSuccessful() && response.body() != null) {
                                result.setValue(Resource.success(true));
                            } else if (response.code() == 409) {
                                result.setValue(Resource.error("Booking này đã được đánh giá", false));
                            } else {
                                result.setValue(Resource.error(
                                        "Không lưu được đánh giá (HTTP " + response.code() + ")", false));
                            }
                        }

                        @Override
                        public void onFailure(Call<ReviewApi.CreateResponse> call, Throwable t) {
                            result.setValue(Resource.error(
                                    "Không thể kết nối máy chủ đánh giá", false));
                        }
                    });
        });
    }

    public void detachReviewsListener() {
        if (reviewsListener != null) {
            reviewsListener.remove();
            reviewsListener = null;
        }
    }
}
