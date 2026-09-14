package com.example.footballbooking.ui.customer.booking;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.footballbooking.data.model.Booking;
import com.example.footballbooking.data.model.Pitch;
import com.example.footballbooking.data.model.Service;
import com.example.footballbooking.data.model.TimeSlot;
import com.example.footballbooking.data.model.User;
import com.example.footballbooking.data.repository.BookingRepository;
import com.example.footballbooking.utils.Constants;
import com.example.footballbooking.utils.Resource;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * BookingViewModel — Quản lý toàn bộ state của màn hình đặt sân.
 *
 * STATE MACHINE của booking:
 * 1. Chọn ngày  → selectedDate thay đổi → load lại slots
 * 2. Chọn slot  → selectedSlot thay đổi → tính lại giá
 * 3. Toggle service → selectedServices thay đổi → tính lại giá
 * 4. Confirm  → gọi createBooking() → navigate đến Payment
 */
public class BookingViewModel extends ViewModel {

    private final BookingRepository bookingRepository;

    // ===== LiveData cho màn hình booking =====
    private final MutableLiveData<Resource<List<Booking>>> bookingHistory = new MutableLiveData<>();
    private final MutableLiveData<Resource<List<TimeSlot>>> availableSlots = new MutableLiveData<>();
    private final MutableLiveData<Resource<Booking>>  createBookingResult = new MutableLiveData<>();
    private final MutableLiveData<Resource<Boolean>>  cancelBookingResult = new MutableLiveData<>();

    // ===== State đang chọn của user =====
    private Pitch selectedPitch;           // Sân đang xem
    private User  currentUser;             // User đang đăng nhập
    private String selectedDate;           // "2025-06-15"
    private TimeSlot selectedSlot;         // Khung giờ đã chọn
    private final List<Service> selectedServices = new ArrayList<>();  // Dịch vụ đã chọn
    private double totalAmount = 0;

    // Number formatter để hiển thị VND
    private final NumberFormat currencyFormatter =
            NumberFormat.getNumberInstance(new Locale("vi", "VN"));

    public BookingViewModel() {
        bookingRepository = BookingRepository.getInstance();
        // Mặc định chọn ngày hôm nay
        selectedDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                .format(new Date());
    }

    // ============================================================
    // EXPOSE LiveData
    // ============================================================

    public MutableLiveData<Resource<List<Booking>>> getBookingHistory() { return bookingHistory; }
    public MutableLiveData<Resource<List<TimeSlot>>> getAvailableSlots() { return availableSlots; }
    public MutableLiveData<Resource<Booking>> getCreateBookingResult() { return createBookingResult; }
    public MutableLiveData<Resource<Boolean>> getCancelBookingResult() { return cancelBookingResult; }

    public String getSelectedDate() { return selectedDate; }
    public TimeSlot getSelectedSlot() { return selectedSlot; }
    public double getTotalAmount() { return totalAmount; }
    public String getFormattedTotal() { return currencyFormatter.format((long) totalAmount) + "đ"; }

    // ============================================================
    // INIT: Thiết lập context (gọi từ Activity sau khi nhận Intent)
    // ============================================================

    public void initBooking(Pitch pitch, User user) {
        this.selectedPitch = pitch;
        this.currentUser   = user;
        // Load slots cho ngày hôm nay
        loadSlotsForDate(selectedDate);
    }

    // ============================================================
    // ACTIONS
    // ============================================================

    /** Gọi khi user chọn ngày mới từ CalendarView */
    public void onDateSelected(long dateMillis) {
        selectedDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                .format(new Date(dateMillis));
        selectedSlot = null;         // Reset slot khi đổi ngày
        selectedServices.clear();    // Reset services
        recalculateTotal();
        loadSlotsForDate(selectedDate);
    }

    /** Gọi khi user chọn/bỏ chọn 1 khung giờ */
    public void onSlotSelected(TimeSlot slot) {
        if (slot.equals(selectedSlot)) {
            selectedSlot = null; // Toggle bỏ chọn
        } else {
            selectedSlot = slot;
        }
        recalculateTotal();
    }

    /** Toggle service: thêm nếu chưa có, bỏ nếu đã có */
    public void toggleService(Service service, boolean isChecked) {
        if (isChecked) {
            if (!selectedServices.contains(service)) selectedServices.add(service);
        } else {
            selectedServices.remove(service);
        }
        recalculateTotal();
    }

    /** Kiểm tra xem booking có thể xác nhận không */
    public boolean canConfirmBooking() {
        return selectedPitch != null
                && selectedDate != null
                && selectedSlot != null
                && currentUser != null;
    }

    /** Tạo đơn đặt sân */
    public void confirmBooking(String note) {
        if (!canConfirmBooking()) return;

        Booking booking = buildBookingObject(note);
        bookingRepository.createBooking(booking, createBookingResult);
    }

    /** Load lịch sử đặt sân của user hiện tại */
    public void loadBookingHistory(String customerId) {
        bookingRepository.getBookingsByCustomer(customerId, bookingHistory);
    }

    /**
     * Hủy đơn — kiểm tra điều kiện trước khi cho phép.
     * Chỉ hủy được khi:
     * - bookingStatus = "pending" hoặc "approved"
     * - Thời gian hiện tại < giờ thi đấu
     */
    public void cancelBooking(Booking booking) {
        // Kiểm tra có thể hủy không
        if (!booking.isCancellable()) {
            cancelBookingResult.setValue(
                    Resource.error("Không thể hủy đơn này", false));
            return;
        }

        // Kiểm tra thời gian: phải trước giờ thi đấu
        if (isPastMatchTime(booking)) {
            cancelBookingResult.setValue(
                    Resource.error("Không thể hủy sau giờ thi đấu", false));
            return;
        }

        bookingRepository.cancelBooking(booking, cancelBookingResult);
    }

    // ============================================================
    // PRIVATE HELPERS
    // ============================================================

    private void loadSlotsForDate(String date) {
        if (selectedPitch == null) return;
        bookingRepository.getAvailableSlots(selectedPitch.getPitchId(), date, availableSlots);
    }

    /** Tính lại tổng tiền theo các lựa chọn hiện tại */
    private void recalculateTotal() {
        if (selectedPitch == null || selectedSlot == null) {
            totalAmount = 0;
            return;
        }

        double basePrice  = selectedPitch.getBasePrice();
        double surcharge  = selectedSlot.isPeakHour() ? selectedSlot.getSurcharge() : 0;
        double serviceTotal = 0;
        for (Service s : selectedServices) serviceTotal += s.getPrice();

        totalAmount = basePrice + surcharge + serviceTotal;
    }

    /** Build Booking object từ tất cả state hiện tại */
    private Booking buildBookingObject(String note) {
        Booking booking = new Booking();

        // Customer info (denormalized)
        booking.setCustomerId(currentUser.getUid());
        booking.setCustomerName(currentUser.getDisplayName());
        booking.setCustomerPhone(currentUser.getPhoneNumber());

        // Pitch info (denormalized)
        booking.setPitchId(selectedPitch.getPitchId());
        booking.setPitchName(selectedPitch.getName());
        booking.setPitchAddress(selectedPitch.getAddress());
        booking.setPitchType(selectedPitch.getType());

        // Booking info
        booking.setBookingDate(selectedDate);
        booking.setSlotId(selectedSlot.getSlotId());
        booking.setStartTime(selectedSlot.getStartTime());
        booking.setEndTime(selectedSlot.getEndTime());

        // Services (convert List<Service> → List<Map>)
        List<Map<String, Object>> servicesMapped = new ArrayList<>();
        double totalServicePrice = 0;
        for (Service s : selectedServices) {
            Map<String, Object> serviceMap = new HashMap<>();
            serviceMap.put("serviceId", s.getServiceId());
            serviceMap.put("serviceName", s.getName());
            serviceMap.put("price", s.getPrice());
            servicesMapped.add(serviceMap);
            totalServicePrice += s.getPrice();
        }
        booking.setServices(servicesMapped);
        booking.setTotalServicePrice(totalServicePrice);

        // Pricing
        double surcharge = selectedSlot.isPeakHour() ? selectedSlot.getSurcharge() : 0;
        booking.setBasePrice(selectedPitch.getBasePrice());
        booking.setSurcharge(surcharge);
        booking.setTotalAmount(totalAmount);

        // Defaults
        booking.setPaymentMethod(Constants.METHOD_VNPAY);
        booking.setNote(note != null ? note : "");

        return booking;
    }

    /** Kiểm tra đã qua giờ thi đấu chưa */
    private boolean isPastMatchTime(Booking booking) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
            Date matchDateTime = sdf.parse(booking.getBookingDate() + " " + booking.getStartTime());
            return matchDateTime != null && new Date().after(matchDateTime);
        } catch (Exception e) {
            return false; // Nếu parse lỗi, cho phép hủy
        }
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        bookingRepository.detachBookingListListener();
    }
}
