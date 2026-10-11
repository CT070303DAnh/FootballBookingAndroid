package com.example.footballbooking.ui.customer.booking;

import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.bumptech.glide.Glide;
import com.example.footballbooking.R;
import com.example.footballbooking.data.model.Pitch;
import com.example.footballbooking.data.model.Service;
import com.example.footballbooking.data.model.TimeSlot;
import com.example.footballbooking.data.model.User;
import com.example.footballbooking.data.repository.AuthRepository;
import com.example.footballbooking.data.repository.PitchRepository;
import com.example.footballbooking.databinding.ActivityBookingBinding;
import com.example.footballbooking.ui.common.adapter.TimeSlotAdapter;
import com.example.footballbooking.utils.Constants;
import com.example.footballbooking.utils.Resource;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;

import java.util.List;

/**
 * BookingActivity — Màn hình chọn ngày, giờ, dịch vụ và đặt sân.
 *
 * FLOW trong Activity này:
 * 1. Nhận pitchId từ Intent
 * 2. Load thông tin sân + user hiện tại
 * 3. User chọn ngày → load available slots
 * 4. User chọn slot → enable nút xác nhận
 * 5. User toggle services → cập nhật tổng tiền
 * 6. Confirm → createBooking → navigate to PaymentActivity
 */
public class BookingActivity extends AppCompatActivity
        implements TimeSlotAdapter.OnSlotClickListener {

    private ActivityBookingBinding binding;
    private BookingViewModel bookingViewModel;
    private TimeSlotAdapter timeSlotAdapter;
    private ServiceSelectAdapter serviceAdapter; // Inner class bên dưới
    private boolean lockedSelection;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityBookingBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        String pitchId = getIntent().getStringExtra(Constants.EXTRA_PITCH_ID);
        if (pitchId == null) { finish(); return; }

        String preselectedDate = getIntent().getStringExtra(Constants.EXTRA_BOOKING_DATE);
        String preselectedSlotId = getIntent().getStringExtra(Constants.EXTRA_SLOT_ID);
        lockedSelection = preselectedDate != null && !preselectedDate.trim().isEmpty()
                && preselectedSlotId != null && !preselectedSlotId.trim().isEmpty();

        bookingViewModel = new ViewModelProvider(this).get(BookingViewModel.class);
        bookingViewModel.preselectDateAndSlot(
                preselectedDate,
                preselectedSlotId);

        setupToolbar();
        setupRecyclerViews();
        setupCalendar();
        applySelectionMode();
        setupPaymentMethodSelection();
        setupConfirmButton();
        loadInitialData(pitchId);
        observeViewModel();
    }

    private void setupToolbar() {
        binding.toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
    }

    private void setupPaymentMethodSelection() {
        binding.rgPaymentMethod.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == binding.rbVnpay.getId()) {
                binding.btnConfirmBooking.setText("Xác nhận và Thanh toán (VietQR) 💳");
            } else if (checkedId == binding.rbCash.getId()) {
                binding.btnConfirmBooking.setText("Xác nhận đặt sân (Tiền mặt) 💵");
            } else if (checkedId == binding.rbPayLater.getId()) {
                binding.btnConfirmBooking.setText("Xác nhận giữ chỗ (Thanh toán sau) ⏳");
            }
        });
    }

    /**
     * When the user comes from "Tìm sân theo giờ", date and slot are already
     * confirmed by the availability screen. Keep the booking screen focused
     * on services, notes and payment instead of asking for the same input again.
     */
    private void applySelectionMode() {
        if (!lockedSelection) return;

        binding.layoutBookingDateSelection.setVisibility(View.GONE);
        binding.layoutBookingSlotSelection.setVisibility(View.GONE);
        binding.tvBookingServicesTitle.setText("1. Dịch vụ kèm theo (tuỳ chọn)");
        binding.tvBookingNoteTitle.setText("2. Ghi chú");
        binding.tvBookingPaymentTitle.setText("3. Phương thức thanh toán");
    }

    private void setupRecyclerViews() {
        // Slots: Grid 2 cột
        timeSlotAdapter = new TimeSlotAdapter(this);
        binding.rvTimeSlots.setLayoutManager(new GridLayoutManager(this, 2));
        binding.rvTimeSlots.setAdapter(timeSlotAdapter);

        // Services: Linear list
        serviceAdapter = new ServiceSelectAdapter(bookingViewModel::isServiceSelected, (service, isChecked) -> {
            bookingViewModel.toggleService(service, isChecked);
            updatePriceUI();
        });
        binding.rvServices.setLayoutManager(new LinearLayoutManager(this));
        binding.rvServices.setAdapter(serviceAdapter);
    }

    private void setupCalendar() {
        binding.calendarView.setMinDate(System.currentTimeMillis() - 1000);
        try {
            java.text.SimpleDateFormat format = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US);
            binding.calendarView.setDate(format.parse(bookingViewModel.getSelectedDate()).getTime());
        } catch (java.text.ParseException ignored) { }
        binding.calendarView.setOnDateChangeListener((view, year, month, dayOfMonth) -> {
            // CalendarView trả về month 0-indexed
            java.util.Calendar cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
            cal.clear();
            cal.set(year, month, dayOfMonth);
            bookingViewModel.onDateSelected(cal.getTimeInMillis());
            timeSlotAdapter.setSelectedSlotId(null);
            serviceAdapter.notifyDataSetChanged();
            updatePriceUI();
        });
    }

    private void setupConfirmButton() {
        binding.btnConfirmBooking.setOnClickListener(v -> {
            if (bookingViewModel.getSelectedSlot() == null) {
                showBookingValidationError("Vui lòng chọn một khung giờ còn trống!");
                return;
            }
            if (!bookingViewModel.canConfirmBooking()) {
                showBookingValidationError(bookingViewModel.getConfirmationError());
                return;
            }
            showConfirmDialog();
        });
    }

    private void showBookingValidationError(String message) {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Chưa thể đặt sân")
                .setMessage(message)
                .setPositiveButton("Đã hiểu", null)
                .show();
    }

    private void showConfirmDialog() {
        TimeSlot slot = bookingViewModel.getSelectedSlot();
        Pitch pitch = bookingViewModel.getSelectedPitch();
        if (slot == null || pitch == null) return;
        final String confirmedDate = bookingViewModel.getSelectedDate();
        final String confirmedSlot = slot.getSlotId();
        final double confirmedAmount = bookingViewModel.getTotalAmount();

        final String paymentMethod = binding.rbCash.isChecked() ? Constants.METHOD_CASH
                : binding.rbPayLater.isChecked() ? Constants.METHOD_PAY_LATER : Constants.METHOD_PAYOS;
        final String paymentLabel = binding.rbCash.isChecked() ? "Tiền mặt tại sân"
                : binding.rbPayLater.isChecked() ? "Thanh toán sau" : "PayOS - VietQR";

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Xác nhận đặt sân")
                .setMessage("• Sân: " + pitch.getName() + "\n"
                        + "• Ngày: " + bookingViewModel.getSelectedDate() + "\n"
                        + "• Khung giờ: " + slot.getStartTime() + " - " + slot.getEndTime() + "\n"
                        + "• Tổng tiền: " + bookingViewModel.getFormattedTotal()
                        + "\n• Thanh toán: " + paymentLabel
                        + (Constants.METHOD_PAYOS.equals(paymentMethod)
                           ? "\nBạn có tối đa 15 phút từ lúc tạo đơn để thanh toán online. Quá hạn, chỗ có thể được mở lại." : ""))
                .setPositiveButton("Xác nhận", (dialog, which) -> {
                    TimeSlot current = bookingViewModel.getSelectedSlot();
                    if (!bookingViewModel.canConfirmBooking() || current == null
                            || !confirmedSlot.equals(current.getSlotId())
                            || !confirmedDate.equals(bookingViewModel.getSelectedDate())
                            || Double.compare(confirmedAmount, bookingViewModel.getTotalAmount()) != 0) {
                        String error = bookingViewModel.getConfirmationError();
                        showBookingValidationError(error != null ? error
                                : "Thông tin đã thay đổi. Vui lòng kiểm tra và xác nhận lại.");
                        return;
                    }
                    binding.btnConfirmBooking.setEnabled(false);
                    String note = binding.etNote.getText() != null
                            ? binding.etNote.getText().toString() : "";
                    bookingViewModel.confirmBooking(note, paymentMethod);
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    /** Load thông tin sân và user để khởi tạo ViewModel */
    private void loadInitialData(String pitchId) {
        bookingViewModel.loadInitialData(pitchId);
    }

    private void bindPitchInfo(Pitch pitch) {
        binding.tvBookingPitchName.setText(pitch.getName());
        binding.tvBookingPitchType.setText("Sân " + pitch.getType() + " người");
        if (pitch.getImageUrls() != null && !pitch.getImageUrls().isEmpty()) {
            Glide.with(this).load(pitch.getImageUrls().get(0))
                    .placeholder(R.drawable.bg_home_header)
                    .error(R.drawable.bg_auth_header)
                    .centerCrop().into(binding.ivPitchThumb);
        } else {
            binding.ivPitchThumb.setImageResource(R.drawable.bg_home_header);
        }
    }

    private void observeViewModel() {
        // Observe Pitch
        bookingViewModel.getPitchLiveData().observe(this, resource -> {
            if (resource != null && resource.isSuccess() && resource.data != null) {
                bindPitchInfo(resource.data);
                bookingViewModel.setSelectedPitch(resource.data);
            }
        });

        // Observe User
        bookingViewModel.getUserLiveData().observe(this, resource -> {
            if (resource != null && resource.isSuccess()) {
                if (resource.data != null) {
                    bookingViewModel.setCurrentUser(resource.data);
                } else {
                    com.google.firebase.auth.FirebaseUser fbUser = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
                    if (fbUser != null) {
                        User user = new User();
                        user.setUid(fbUser.getUid());
                        user.setDisplayName(fbUser.getDisplayName() != null && !fbUser.getDisplayName().isEmpty() ? fbUser.getDisplayName() : "Khách hàng");
                        user.setEmail(fbUser.getEmail() != null ? fbUser.getEmail() : "");
                        user.setPhoneNumber(fbUser.getPhoneNumber() != null ? fbUser.getPhoneNumber() : "");
                        bookingViewModel.setCurrentUser(user);
                    }
                }
            }
        });

        // Slots available
        bookingViewModel.getAvailableSlots().observe(this, resource -> {
            if (resource == null) return;
            if (resource.isLoading()) {
                binding.shimmerSlots.setVisibility(View.VISIBLE);
                binding.shimmerSlots.startShimmer();
            } else {
                binding.shimmerSlots.stopShimmer();
                binding.shimmerSlots.setVisibility(View.GONE);
            }

            if (resource.isSuccess() && resource.data != null) {
                List<TimeSlot> slots = resource.data;
                timeSlotAdapter.submitList(slots);
                binding.tvNoSlots.setText("Không có khung giờ phù hợp");
                binding.tvNoSlots.setVisibility(slots.isEmpty() ? View.VISIBLE : View.GONE);
            } else if (resource.isError()) {
                timeSlotAdapter.submitList(java.util.Collections.emptyList());
                binding.tvNoSlots.setVisibility(View.VISIBLE);
                binding.tvNoSlots.setText("Lỗi tải khung giờ: " + resource.message);
                Snackbar.make(binding.getRoot(),
                        "Lỗi tải khung giờ: " + resource.message, Snackbar.LENGTH_SHORT).show();
            }
            if (resource.isLoading()) timeSlotAdapter.submitList(java.util.Collections.emptyList());
            TimeSlot selected = bookingViewModel.getSelectedSlot();
            timeSlotAdapter.setSelectedSlotId(selected == null ? null : selected.getSlotId());
            updatePriceUI();
        });

        // Dịch vụ kèm theo
        bookingViewModel.getServicesLiveData().observe(this, resource -> {
            if (resource != null && resource.isSuccess() && resource.data != null) {
                serviceAdapter.setServices(resource.data);
                updatePriceUI();
            }
        });

        // Kết quả tạo booking
        bookingViewModel.getCreateBookingResult().observe(this, resource -> {
            if (resource == null) return;
            binding.btnConfirmBooking.setEnabled(!resource.isLoading());

            if (resource.isSuccess() && resource.data != null) {
                com.example.footballbooking.data.model.Booking createdBooking = resource.data;
                if (Constants.METHOD_PAYOS.equals(createdBooking.getPaymentMethod())
                        || Constants.METHOD_VNPAY.equals(createdBooking.getPaymentMethod())) {
                    android.content.Intent intent = new android.content.Intent(this,
                            com.example.footballbooking.ui.customer.payment.PaymentActivity.class);
                    intent.putExtra(Constants.EXTRA_BOOKING_ID, createdBooking.getBookingId());
                    startActivity(intent);
                    finish();
                } else if (Constants.METHOD_PAY_LATER.equals(createdBooking.getPaymentMethod())) {
                    new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                            .setTitle("Giữ chỗ thành công! ⏳")
                            .setMessage("Đơn đặt sân đã được giữ chỗ thành công.\nMã đơn: #"
                                    + createdBooking.getBookingId()
                                    + "\nBạn có thể thanh toán trực tuyến bất cứ lúc nào trong mục Lịch sử đặt sân.")
                            .setPositiveButton("Đồng ý", (dialog, which) -> finish())
                            .setCancelable(false)
                            .show();
                } else {
                    new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                            .setTitle("Đặt sân thành công! 🎉")
                            .setMessage("Đơn đặt sân của bạn đã được gửi đến chủ sân.\nMã đơn: #"
                                    + createdBooking.getBookingId()
                                    + "\nVui lòng thanh toán tiền mặt khi đến nhận sân.")
                            .setPositiveButton("Đồng ý", (dialog, which) -> finish())
                            .setCancelable(false)
                            .show();
                }
            } else if (resource.isError()) {
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                        .setTitle("⚠️ Thông báo đặt sân")
                        .setMessage(resource.message != null ? resource.message : "Khung giờ này đã được đặt rồi!")
                        .setPositiveButton("Đồng ý", null)
                        .show();
            }
        });
    }

    // --- TimeSlotAdapter Callback ---
    @Override
    public void onSlotClick(TimeSlot slot) {
        if (!slot.isAvailable()) {
            String msg = "past".equalsIgnoreCase(slot.getStatus())
                    ? "Khung giờ này đã quá thời gian, không thể đặt!"
                    : "Khung giờ này đã có người đặt rồi! Vui lòng chọn khung giờ khác.";
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                    .setTitle("⚠️ Thông báo khung giờ")
                    .setMessage(msg)
                    .setPositiveButton("Đồng ý", null)
                    .show();
            return;
        }
        bookingViewModel.onSlotSelected(slot);
        timeSlotAdapter.setSelectedSlotId(
                bookingViewModel.getSelectedSlot() != null ? bookingViewModel.getSelectedSlot().getSlotId() : null);

        // Cập nhật UI tổng tiền và nút xác nhận
        updatePriceUI();
    }

    private void updatePriceUI() {
        binding.tvTotalAmount.setText(bookingViewModel.getFormattedTotal());
        if (bookingViewModel.getSelectedSlot() != null) {
            binding.layoutPriceBreakdown.setVisibility(View.VISIBLE);
            binding.tvBasePrice.setText(bookingViewModel.getFormattedBasePrice());

            if (bookingViewModel.getSurcharge() > 0) {
                binding.layoutSurcharge.setVisibility(View.VISIBLE);
                binding.tvSurcharge.setText(bookingViewModel.getFormattedSurcharge());
            } else {
                binding.layoutSurcharge.setVisibility(View.GONE);
            }

            if (bookingViewModel.getServicePrice() > 0) {
                binding.layoutServicePrice.setVisibility(View.VISIBLE);
                binding.tvServicePrice.setText(bookingViewModel.getFormattedServicePrice());
            } else {
                binding.layoutServicePrice.setVisibility(View.GONE);
            }
        } else {
            binding.layoutPriceBreakdown.setVisibility(View.GONE);
        }
    }

    // ============================================================
    // SERVICE ADAPTER (Inner class đơn giản)
    // ============================================================

    /**
     * Adapter nội bộ cho danh sách dịch vụ — có checkbox toggle.
     * Tách ra class riêng nếu sau này cần tái sử dụng.
     */
    static class ServiceSelectAdapter
            extends androidx.recyclerview.widget.RecyclerView.Adapter<ServiceSelectAdapter.VH> {

        interface OnServiceToggleListener {
            void onToggle(Service service, boolean isChecked);
        }

        private List<Service> services;
        private final OnServiceToggleListener listener;
        private final java.util.function.Predicate<Service> isSelected;

        ServiceSelectAdapter(java.util.function.Predicate<Service> isSelected,
                             OnServiceToggleListener listener) {
            this.isSelected = isSelected;
            this.listener = listener;
        }

        public void setServices(List<Service> services) {
            this.services = services;
            notifyDataSetChanged();
        }

        @Override @androidx.annotation.NonNull
        public VH onCreateViewHolder(@androidx.annotation.NonNull android.view.ViewGroup p, int t) {
            com.example.footballbooking.databinding.ItemServiceSelectBinding b =
                    com.example.footballbooking.databinding.ItemServiceSelectBinding.inflate(
                            android.view.LayoutInflater.from(p.getContext()), p, false);
            return new VH(b);
        }

        @Override
        public void onBindViewHolder(@androidx.annotation.NonNull VH h, int pos) {
            if (services == null) return;
            Service s = services.get(pos);
            h.b.cbService.setChecked(isSelected.test(s));
            h.b.tvServiceName.setText(s.getName());
            h.b.tvServiceDesc.setText(s.getDescription() + " / " + s.getUnit());
            h.b.tvServicePrice.setText("+" +
                    java.text.NumberFormat.getNumberInstance(new java.util.Locale("vi", "VN"))
                    .format((long) s.getPrice()) + "đ");

            // Card toggle
            h.b.cardService.setOnClickListener(v -> {
                listener.onToggle(s, !isSelected.test(s));
                h.b.cbService.setChecked(isSelected.test(s));
            });
        }

        @Override
        public int getItemCount() { return services != null ? services.size() : 0; }

        static class VH extends androidx.recyclerview.widget.RecyclerView.ViewHolder {
            final com.example.footballbooking.databinding.ItemServiceSelectBinding b;
            VH(com.example.footballbooking.databinding.ItemServiceSelectBinding b) {
                super(b.getRoot()); this.b = b;
            }
        }
    }
}
