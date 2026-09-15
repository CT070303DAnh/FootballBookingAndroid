package com.example.footballbooking.ui.customer.booking;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.footballbooking.data.model.Booking;
import com.example.footballbooking.databinding.FragmentBookingHistoryBinding;
import com.example.footballbooking.ui.common.adapter.BookingAdapter;
import com.example.footballbooking.utils.Constants;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * BookingHistoryFragment — Lịch sử đặt sân với realtime updates.
 * Hiển thị trạng thái dạng màu sắc trực quan và cho phép hủy đơn.
 */
public class BookingHistoryFragment extends Fragment
        implements BookingAdapter.OnBookingActionListener {

    private FragmentBookingHistoryBinding binding;
    private BookingViewModel bookingViewModel;
    private BookingAdapter bookingAdapter;

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

        setupRecyclerView();
        loadHistory();
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
                            + "• Trạng thái: Chưa thanh toán (Thanh toán sau)\n\n"
                            + "Bạn có muốn thanh toán qua VNPay ngay bây giờ không?")
                    .setPositiveButton("Thanh toán ngay (VNPay)", (dialog, which) -> {
                        android.content.Intent intent = new android.content.Intent(requireContext(),
                                com.example.footballbooking.ui.customer.payment.PaymentActivity.class);
                        intent.putExtra(Constants.EXTRA_BOOKING_ID, booking.getBookingId());
                        intent.putExtra("extra_amount", (long) booking.getTotalAmount());
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
                            + "• Thanh toán: " + (Constants.PAYMENT_PAID.equals(booking.getPaymentStatus()) ? "Đã thanh toán" : "Chưa thanh toán"))
                    .setPositiveButton("Đóng", null)
                    .show();
        }
    }

    private void showEmpty(boolean empty) {
        binding.layoutEmptyHistory.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.rvBookingHistory.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
