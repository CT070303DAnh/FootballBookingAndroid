package com.example.footballbooking.ui.customer.booking;

import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.RatingBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.footballbooking.data.model.Booking;
import com.example.footballbooking.data.repository.ReviewRepository;
import com.example.footballbooking.databinding.FragmentBookingHistoryBinding;
import com.example.footballbooking.ui.common.adapter.BookingAdapter;
import com.example.footballbooking.utils.Constants;
import com.example.footballbooking.utils.Resource;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.Set;

/**
 * BookingHistoryFragment — Lịch sử đặt sân với realtime updates.
 * Hiển thị trạng thái dạng màu sắc trực quan và cho phép hủy đơn.
 */
public class BookingHistoryFragment extends Fragment
        implements BookingAdapter.OnBookingActionListener {

    private FragmentBookingHistoryBinding binding;
    private BookingViewModel bookingViewModel;
    private BookingAdapter bookingAdapter;
    private ReviewRepository reviewRepository;
    private final androidx.lifecycle.MutableLiveData<Resource<Set<String>>> reviewedBookingIds =
            new androidx.lifecycle.MutableLiveData<>();
    private final androidx.lifecycle.MutableLiveData<Resource<Boolean>> createReviewResult =
            new androidx.lifecycle.MutableLiveData<>();
    private final android.os.Handler clockHandler =
            new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable clockRefresh = new Runnable() {
        @Override
        public void run() {
            if (binding != null && bookingAdapter != null) {
                bookingAdapter.refreshTimeSensitiveState();
                clockHandler.postDelayed(this, 30_000L);
            }
        }
    };

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentBookingHistoryBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        bookingViewModel = new ViewModelProvider(this).get(BookingViewModel.class);
        reviewRepository = ReviewRepository.createScoped();

        setupRecyclerView();
        loadHistory();
        loadReviewedBookings();
        observeViewModel();
    }

    private void setupRecyclerView() {
        bookingAdapter = new BookingAdapter(this);
        binding.rvBookingHistory.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvBookingHistory.setAdapter(bookingAdapter);
    }

    private void loadHistory() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            bookingViewModel.loadBookingHistory(user.getUid());
        }
    }

    private void loadReviewedBookings() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) reviewRepository.getReviewedBookingIds(user.getUid(), reviewedBookingIds);
    }

    @Override
    public void onStart() {
        super.onStart();
        clockHandler.removeCallbacks(clockRefresh);
        clockHandler.post(clockRefresh);
    }

    @Override
    public void onStop() {
        clockHandler.removeCallbacks(clockRefresh);
        super.onStop();
    }

    private void observeViewModel() {
        bookingViewModel.getBookingHistory().observe(getViewLifecycleOwner(), resource -> {
            if (resource == null) return;
            switch (resource.status) {
                case LOADING:
                    binding.progressHistory.setVisibility(View.VISIBLE);
                    break;
                case SUCCESS:
                    binding.progressHistory.setVisibility(View.GONE);
                    if (resource.data == null || resource.data.isEmpty()) {
                        showEmpty(true);
                    } else {
                        showEmpty(false);
                        bookingAdapter.submitList(resource.data);
                    }
                    break;
                case ERROR:
                    binding.progressHistory.setVisibility(View.GONE);
                    Snackbar.make(binding.getRoot(),
                            "Lỗi: " + resource.message, Snackbar.LENGTH_LONG).show();
                    break;
            }
        });

        // Observe kết quả hủy đơn
        bookingViewModel.getCancelBookingResult().observe(getViewLifecycleOwner(), resource -> {
            if (resource == null) return;
            if (resource.isSuccess()) {
                Snackbar.make(binding.getRoot(), "Đã hủy đơn thành công", Snackbar.LENGTH_SHORT).show();
            } else if (resource.isError()) {
                Snackbar.make(binding.getRoot(), resource.message, Snackbar.LENGTH_LONG).show();
            }
        });

        reviewedBookingIds.observe(getViewLifecycleOwner(), resource -> {
            if (resource != null && resource.isSuccess()) {
                bookingAdapter.setReviewedBookingIds(resource.data);
            }
        });

        createReviewResult.observe(getViewLifecycleOwner(), resource -> {
            if (resource == null) return;
            if (resource.isSuccess()) {
                Snackbar.make(binding.getRoot(), "Đã gửi đánh giá sân", Snackbar.LENGTH_SHORT).show();
                loadReviewedBookings();
            } else if (resource.isError()) {
                Snackbar.make(binding.getRoot(), resource.message, Snackbar.LENGTH_LONG).show();
            }
        });
    }

    // --- BookingAdapter Callbacks ---

    @Override
    public void onCancelClick(Booking booking) {
        // Dialog xác nhận trước khi hủy
        new AlertDialog.Builder(requireContext())
                .setTitle("Hủy đơn đặt sân")
                .setMessage("Bạn có chắc muốn hủy đơn đặt sân\n" +
                        booking.getPitchName() + " lúc " + booking.getStartTime() + "?")
                .setPositiveButton("Hủy đơn", (d, w) ->
                        bookingViewModel.cancelBooking(booking))
                .setNegativeButton("Giữ lại", null)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    @Override
    public void onReviewClick(Booking booking) {
        if (booking == null) return;

        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(32, 8, 32, 0);

        RatingBar ratingBar = new RatingBar(requireContext());
        ratingBar.setNumStars(5);
        ratingBar.setStepSize(1f);
        ratingBar.setRating(5f);
        LinearLayout.LayoutParams ratingParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ratingParams.gravity = Gravity.CENTER_HORIZONTAL;
        content.addView(ratingBar, ratingParams);

        TextInputLayout inputLayout = new TextInputLayout(requireContext());
        inputLayout.setHint("Nhận xét (không bắt buộc)");
        inputLayout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        TextInputEditText commentInput = new TextInputEditText(inputLayout.getContext());
        commentInput.setMinLines(3);
        commentInput.setGravity(Gravity.TOP);
        // TextInputLayout extends LinearLayout and requires LinearLayout.LayoutParams.
        // ViewGroup.LayoutParams causes ClassCastException when the dialog opens.
        inputLayout.addView(commentInput, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        inputParams.topMargin = 12;
        content.addView(inputLayout, inputParams);

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Đánh giá " + booking.getPitchName())
                .setView(content)
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Gửi đánh giá", null)
                .create();

        dialog.setOnShowListener(ignored -> dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String comment = commentInput.getText() == null
                            ? "" : commentInput.getText().toString().trim();
                    if (comment.length() > 1000) {
                        inputLayout.setError("Tối đa 1000 ký tự");
                        return;
                    }
                    reviewRepository.createReview(booking.getBookingId(), ratingBar.getRating(),
                            comment, createReviewResult);
                    dialog.dismiss();
                }));
        dialog.show();
    }

    @Override
    public void onBookingClick(Booking booking) {
        if (booking == null) return;
        java.text.NumberFormat nf = java.text.NumberFormat.getNumberInstance(new java.util.Locale("vi", "VN"));
        String shortId = (booking.getBookingId() != null)
                ? booking.getBookingId().substring(0, Math.min(8, booking.getBookingId().length())) : "";

        if (Constants.PAYMENT_UNPAID.equals(booking.getPaymentStatus())
                && !Constants.STATUS_CANCELLED.equals(booking.getBookingStatus())
                && !Constants.STATUS_REJECTED.equals(booking.getBookingStatus())) {
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Đơn đặt sân #" + shortId)
                    .setMessage("• Sân: " + booking.getPitchName() + "\n"
                            + "• Thời gian: " + booking.getBookingDate() + " (" + booking.getStartTime() + " - " + booking.getEndTime() + ")\n"
                            + "• Tổng tiền: " + nf.format((long) booking.getTotalAmount()) + "đ\n"
                            + "• Trạng thái: Chưa thanh toán\n"
                            + "• Phương thức: " + (Constants.METHOD_CASH.equals(booking.getPaymentMethod())
                                ? "Tiền mặt tại sân" : isOnlinePayment(booking.getPaymentMethod()) ? "PayOS - VietQR" : "Phương thức khác") + "\n\n"
                            + "Bạn có muốn thanh toán qua VietQR ngay bây giờ không?")
                    .setPositiveButton("Thanh toán ngay (VietQR)", (dialog, which) -> {
                        android.content.Intent intent = new android.content.Intent(requireContext(),
                                com.example.footballbooking.ui.customer.payment.PaymentActivity.class);
                        intent.putExtra(Constants.EXTRA_BOOKING_ID, booking.getBookingId());
                        startActivity(intent);
                    })
                    .setNegativeButton("Đóng", null)
                    .show();
        } else {
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Đơn đặt sân #" + shortId)
                    .setMessage("• Sân: " + booking.getPitchName() + "\n"
                            + "• Địa chỉ: " + booking.getPitchAddress() + "\n"
                            + "• Thời gian: " + booking.getBookingDate() + " (" + booking.getStartTime() + " - " + booking.getEndTime() + ")\n"
                            + "• Trạng thái: " + booking.getBookingStatus() + "\n"
                            + "• Tổng tiền: " + nf.format((long) booking.getTotalAmount()) + "đ\n"
                            + "• Thanh toán: " + (Constants.PAYMENT_PAID.equals(booking.getPaymentStatus()) ? "Đã thanh toán" : "Chưa thanh toán")
                            + (Constants.PAYMENT_PAID.equals(booking.getPaymentStatus())
                               && (Constants.STATUS_CANCELLED.equals(booking.getBookingStatus()) || Constants.STATUS_REJECTED.equals(booking.getBookingStatus()))
                               ? "\nĐơn đã hủy/từ chối nhưng có thanh toán. Vui lòng liên hệ chủ sân để đối soát/hoàn tiền." : ""))
                    .setNeutralButton(isOnlinePayment(booking.getPaymentMethod()) ? "Kiểm tra thanh toán" : null,
                            (dialog, which) -> {
                                android.content.Intent intent = new android.content.Intent(requireContext(),
                                        com.example.footballbooking.ui.customer.payment.PaymentActivity.class);
                                intent.putExtra(Constants.EXTRA_BOOKING_ID, booking.getBookingId());
                                startActivity(intent);
                            })
                    .setPositiveButton("Đóng", null)
                    .show();
        }
    }

    private boolean isOnlinePayment(String paymentMethod) {
        return Constants.METHOD_PAYOS.equals(paymentMethod);
    }

    private void showEmpty(boolean empty) {
        binding.layoutEmptyHistory.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.rvBookingHistory.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    @Override
    public void onDestroyView() {
        clockHandler.removeCallbacks(clockRefresh);
        if (reviewRepository != null) reviewRepository.detachReviewsListener();
        super.onDestroyView();
        binding = null;
    }
}
