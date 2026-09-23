package com.example.footballbooking.ui.customer.booking;

import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.bumptech.glide.Glide;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityBookingBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        String pitchId = getIntent().getStringExtra(Constants.EXTRA_PITCH_ID);
        if (pitchId == null) { finish(); return; }

        bookingViewModel = new ViewModelProvider(this).get(BookingViewModel.class);

        setupToolbar();
        setupRecyclerViews();
        setupCalendar();
        setupPaymentMethodSelection();
        setupConfirmButton();
        loadInitialData(pitchId);
        observeViewModel();
    }

    private void setupToolbar() {
        binding.toolbar.setNavigationOnClickListener(v -> onBackPressed());
    }

    private void setupPaymentMethodSelection() {
        binding.rgPaymentMethod.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == binding.rbVnpay.getId()) {
                binding.btnConfirmBooking.setText("Xác nhận và Thanh toán (VNPay) 💳");
            } else if (checkedId == binding.rbCash.getId()) {
                binding.btnConfirmBooking.setText("Xác nhận đặt sân (Tiền mặt) 💵");
            } else if (checkedId == binding.rbPayLater.getId()) {
                binding.btnConfirmBooking.setText("Xác nhận giữ chỗ (Thanh toán sau) ⏳");
            }
        });
    }

    private void setupRecyclerViews() {
        // Slots: Grid 2 cột
        timeSlotAdapter = new TimeSlotAdapter(this);
        binding.rvTimeSlots.setLayoutManager(new GridLayoutManager(this, 2));
        binding.rvTimeSlots.setAdapter(timeSlotAdapter);

        // Services: Linear list
        serviceAdapter = new ServiceSelectAdapter((service, isChecked) -> {
            bookingViewModel.toggleService(service, isChecked);
            updatePriceUI();
        });
        binding.rvServices.setLayoutManager(new LinearLayoutManager(this));
        binding.rvServices.setAdapter(serviceAdapter);
    }

    private void setupCalendar() {
        binding.calendarView.setMinDate(System.currentTimeMillis() - 1000);
        binding.calendarView.setOnDateChangeListener((view, year, month, dayOfMonth) -> {
            // CalendarView trả về month 0-indexed
            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.set(year, month, dayOfMonth);
            bookingViewModel.onDateSelected(cal.getTimeInMillis());
            timeSlotAdapter.setSelectedSlotId(null);
            updatePriceUI();
        });
    }

    private void setupConfirmButton() {
        binding.btnConfirmBooking.setOnClickListener(v -> {
            if (bookingViewModel.getSelectedSlot() == null) {
                Snackbar.make(v, "Vui lòng chọn một khung giờ còn trống!", Snackbar.LENGTH_SHORT).show();
                return;
            }
            if (!bookingViewModel.canConfirmBooking()) {
                Snackbar.make(v, "Vui lòng kiểm tra lại thông tin đặt sân!", Snackbar.LENGTH_SHORT).show();
                return;
            }
            showConfirmDialog();
        });
    }

    private void showConfirmDialog() {
        TimeSlot slot = bookingViewModel.getSelectedSlot();
        Pitch pitch = bookingViewModel.getSelectedPitch();
        if (slot == null || pitch == null) return;

        final String[] paymentMethods = {
                "💳 Thanh toán ngay qua VNPay",
                "💵 Thanh toán tại sân (Tiền mặt)",
                "⏳ Thanh toán sau (Giữ chỗ trước)"
        };

        int initialIndex = 0;
        if (binding.rbCash.isChecked()) {
            initialIndex = 1;
        } else if (binding.rbPayLater.isChecked()) {
            initialIndex = 2;
        }

        final int[] selectedMethodIndex = {initialIndex};

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Xác nhận đặt sân")
                .setMessage("• Sân: " + pitch.getName() + "\n"
                        + "• Ngày: " + bookingViewModel.getSelectedDate() + "\n"
                        + "• Khung giờ: " + slot.getStartTime() + " - " + slot.getEndTime() + "\n"
                        + "• Tổng tiền: " + bookingViewModel.getFormattedTotal())
                .setSingleChoiceItems(paymentMethods, selectedMethodIndex[0], (dialog, which) -> {
                    selectedMethodIndex[0] = which;
                    if (which == 0) binding.rbVnpay.setChecked(true);
                    else if (which == 1) binding.rbCash.setChecked(true);
                    else if (which == 2) binding.rbPayLater.setChecked(true);
                })
                .setPositiveButton("Xác nhận", (dialog, which) -> {
                    String note = binding.etNote.getText() != null
                            ? binding.etNote.getText().toString() : "";
                    String method;
                    if (selectedMethodIndex[0] == 0) {
                        method = Constants.METHOD_VNPAY;
                    } else if (selectedMethodIndex[0] == 1) {
                        method = Constants.METHOD_CASH;
                    } else {
                        method = Constants.METHOD_PAY_LATER;
                    }
                    bookingViewModel.confirmBooking(note, method);
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    /** Load thông tin sân và user để khởi tạo ViewModel */
    private void loadInitialData(String pitchId) {
        // Load pitch
        PitchRepository.getInstance().getPitchById(pitchId,
                new androidx.lifecycle.MutableLiveData<Resource<Pitch>>() {{
                    observe(BookingActivity.this, resource -> {
                        if (resource != null && resource.isSuccess() && resource.data != null) {
                            bindPitchInfo(resource.data);
                            // Load user và init booking
                            loadCurrentUserAndInit(resource.data);
                        }
                    });
                }});
    }

    private void loadCurrentUserAndInit(Pitch pitch) {
        AuthRepository.getInstance().checkCurrentUser(
                new androidx.lifecycle.MutableLiveData<Resource<User>>() {{
                    observe(BookingActivity.this, resource -> {
                        if (resource != null && resource.isSuccess() && resource.data != null) {
                            bookingViewModel.initBooking(pitch, resource.data);
                        }
                    });
                }});
    }

    private void bindPitchInfo(Pitch pitch) {
        binding.tvBookingPitchName.setText(pitch.getName());
        binding.tvBookingPitchType.setText("Sân " + pitch.getType() + " người");
        if (pitch.getImageUrls() != null && !pitch.getImageUrls().isEmpty()) {
            Glide.with(this).load(pitch.getImageUrls().get(0))
                    .placeholder(com.example.footballbooking.R.drawable.bg_home_header)
                    .error(com.example.footballbooking.R.drawable.bg_auth_header)
                    .centerCrop().into(binding.ivPitchThumb);
        }
    }

    private void observeViewModel() {
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
                binding.tvNoSlots.setVisibility(slots.isEmpty() ? View.VISIBLE : View.GONE);
            } else if (resource.isError()) {
                binding.tvNoSlots.setVisibility(View.VISIBLE);
                binding.tvNoSlots.setText("Lỗi tải khung giờ: " + resource.message);
                Snackbar.make(binding.getRoot(),
                        "Lỗi tải khung giờ: " + resource.message, Snackbar.LENGTH_SHORT).show();
            }
        });

        // Dịch vụ kèm theo
        bookingViewModel.getServicesLiveData().observe(this, resource -> {
            if (resource != null && resource.isSuccess() && resource.data != null) {
                serviceAdapter.setServices(resource.data);
            }
        });

        // Kết quả tạo booking
        bookingViewModel.getCreateBookingResult().observe(this, resource -> {
            if (resource == null) return;
            binding.btnConfirmBooking.setEnabled(!resource.isLoading());

            if (resource.isSuccess() && resource.data != null) {
                com.example.footballbooking.data.model.Booking createdBooking = resource.data;
                if (Constants.METHOD_VNPAY.equals(createdBooking.getPaymentMethod())) {
                    android.content.Intent intent = new android.content.Intent(this,
                            com.example.footballbooking.ui.customer.payment.PaymentActivity.class);
                    intent.putExtra(Constants.EXTRA_BOOKING_ID, createdBooking.getBookingId());
                    intent.putExtra("extra_amount", (long) createdBooking.getTotalAmount());
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
                Snackbar.make(binding.getRoot(),
                        "Đặt sân thất bại: " + resource.message, Snackbar.LENGTH_LONG).show();
            }
        });
    }

    // --- TimeSlotAdapter Callback ---
    @Override
    public void onSlotClick(TimeSlot slot) {
        if (!slot.isAvailable()) return;
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

        ServiceSelectAdapter(OnServiceToggleListener listener) {
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
            h.b.tvServiceName.setText(s.getName());
            h.b.tvServiceDesc.setText(s.getDescription() + " / " + s.getUnit());
            h.b.tvServicePrice.setText("+" +
                    java.text.NumberFormat.getNumberInstance(new java.util.Locale("vi", "VN"))
                    .format((long) s.getPrice()) + "đ");

            // Card toggle
            h.b.cardService.setOnClickListener(v -> {
                h.b.cbService.toggle();
                listener.onToggle(s, h.b.cbService.isChecked());
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
