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
        setupConfirmButton();
        loadInitialData(pitchId);
        observeViewModel();
    }

    private void setupToolbar() {
        binding.toolbar.setNavigationOnClickListener(v -> onBackPressed());
    }

    private void setupRecyclerViews() {
        // Slots: Grid 2 cột
        timeSlotAdapter = new TimeSlotAdapter(this);
        binding.rvTimeSlots.setLayoutManager(new GridLayoutManager(this, 2));
        binding.rvTimeSlots.setAdapter(timeSlotAdapter);

        // Services: Linear list
        serviceAdapter = new ServiceSelectAdapter(
                (service, isChecked) -> bookingViewModel.toggleService(service, isChecked));
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
        });
    }

    private void setupConfirmButton() {
        binding.btnConfirmBooking.setOnClickListener(v -> {
            if (!bookingViewModel.canConfirmBooking()) {
                Snackbar.make(v, "Vui lòng chọn ngày và khung giờ", Snackbar.LENGTH_SHORT).show();
                return;
            }
            String note = binding.etNote.getText() != null
                    ? binding.etNote.getText().toString() : "";
            bookingViewModel.confirmBooking(note);
        });
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
                    .centerCrop().into(binding.ivPitchThumb);
        }
    }

    private void observeViewModel() {
        // Slots available
        bookingViewModel.getAvailableSlots().observe(this, resource -> {
            if (resource == null) return;
            if (resource.isSuccess() && resource.data != null) {
                timeSlotAdapter.submitList(resource.data);
            } else if (resource.isError()) {
                Snackbar.make(binding.getRoot(),
                        "Lỗi tải khung giờ: " + resource.message, Snackbar.LENGTH_SHORT).show();
            }
        });

        // Kết quả tạo booking
        bookingViewModel.getCreateBookingResult().observe(this, resource -> {
            if (resource == null) return;
            binding.btnConfirmBooking.setEnabled(!resource.isLoading());

            if (resource.isSuccess() && resource.data != null) {
                // TODO: Navigate sang PaymentActivity với bookingId
                // Intent intent = new Intent(this, PaymentActivity.class);
                // intent.putExtra(Constants.EXTRA_BOOKING_ID, resource.data.getBookingId());
                // startActivity(intent);
                Snackbar.make(binding.getRoot(),
                        "✅ Đặt sân thành công! Chờ Admin duyệt.",
                        Snackbar.LENGTH_LONG).show();
                finish();
            } else if (resource.isError()) {
                Snackbar.make(binding.getRoot(),
                        resource.message, Snackbar.LENGTH_LONG).show();
            }
        });
    }

    // --- TimeSlotAdapter Callback ---
    @Override
    public void onSlotClick(TimeSlot slot) {
        bookingViewModel.onSlotSelected(slot);
        timeSlotAdapter.setSelectedSlotId(slot.getSlotId());

        // Cập nhật UI tổng tiền và nút xác nhận
        updatePriceUI();
        binding.btnConfirmBooking.setEnabled(bookingViewModel.canConfirmBooking());
    }

    private void updatePriceUI() {
        binding.tvTotalAmount.setText(bookingViewModel.getFormattedTotal());
        binding.layoutPriceBreakdown.setVisibility(View.VISIBLE);
        // TODO: set chi tiết breakdown (basePrice, surcharge, servicePrice)
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
