package com.example.footballbooking.ui.customer.booking;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.footballbooking.data.model.Booking;
import com.example.footballbooking.data.model.Pitch;
import com.example.footballbooking.data.model.Service;
import com.example.footballbooking.data.model.TimeSlot;
import com.example.footballbooking.data.model.User;
import com.example.footballbooking.data.repository.AuthRepository;
import com.example.footballbooking.data.repository.BookingRepository;
import com.example.footballbooking.data.repository.PitchRepository;
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
    private final MutableLiveData<Resource<Pitch>> pitchLiveData = new MutableLiveData<>();
    private final MutableLiveData<Resource<User>> userLiveData = new MutableLiveData<>();

    // ===== State đang chọn của user =====
    private Pitch selectedPitch;           // Sân đang xem
    private User  currentUser;             // User đang đăng nhập
    private String selectedDate;           // "2025-06-15"
    private TimeSlot selectedSlot;         // Khung giờ đã chọn
    private final List<Service> selectedServices = new ArrayList<>();  // Dịch vụ đã chọn
    private long servicesGeneration;
    private final androidx.lifecycle.SavedStateHandle savedState;
    private final androidx.lifecycle.Observer<Resource<List<TimeSlot>>> slotsObserver = resource -> {
        String wanted = savedSlotId();
        selectedSlot = null;
        if (resource != null && resource.isSuccess() && resource.data != null) {
            for (TimeSlot slot : resource.data) {
                if (slot.isAvailable() && wanted != null && wanted.equals(slot.getSlotId())) selectedSlot = slot;
            }
            if (selectedSlot == null) savedStateRemoveSlot();
        }
        recalculateTotal();
    };

    private String savedSlotId() { return savedState.get("slotId"); }
    private void savedStateRemoveSlot() { savedState.remove("slotId"); }
    private double totalAmount = 0;

    // Number formatter để hiển thị VND
    private final NumberFormat currencyFormatter =
            NumberFormat.getNumberInstance(new Locale("vi", "VN"));

    public BookingViewModel(androidx.lifecycle.SavedStateHandle savedState) {
        this.savedState = savedState;
        bookingRepository = BookingRepository.createScoped();
        // Mặc định chọn ngày hôm nay
        selectedDate = savedState.get("date");
        String today = bookingDateFormat().format(new Date());
        if (selectedDate == null || selectedDate.compareTo(today) < 0) {
            selectedDate = today;
            savedStateRemoveSlot();
            savedState.remove("serviceIds");
        }
        savedState.set("date", selectedDate);
        availableSlots.observeForever(slotsObserver);
    }

    private final MutableLiveData<Resource<List<Service>>> servicesLiveData = new MutableLiveData<>();

    // ============================================================
    // EXPOSE LiveData
    // ============================================================

    public MutableLiveData<Resource<List<Booking>>> getBookingHistory() { return bookingHistory; }
    public MutableLiveData<Resource<List<TimeSlot>>> getAvailableSlots() { return availableSlots; }
    public MutableLiveData<Resource<List<Service>>> getServicesLiveData() { return servicesLiveData; }
    public MutableLiveData<Resource<Booking>> getCreateBookingResult() { return createBookingResult; }
    public MutableLiveData<Resource<Boolean>> getCancelBookingResult() { return cancelBookingResult; }
    public MutableLiveData<Resource<Pitch>> getPitchLiveData() { return pitchLiveData; }
    public MutableLiveData<Resource<User>> getUserLiveData() { return userLiveData; }

    public String getSelectedDate() { return selectedDate; }
    public TimeSlot getSelectedSlot() { return selectedSlot; }
    public Pitch getSelectedPitch() { return selectedPitch; }
    public User getCurrentUser() { return currentUser; }
    public double getTotalAmount() { return totalAmount; }
    public String getFormattedTotal() { return currencyFormatter.format((long) totalAmount) + "đ"; }

    public double getBasePrice() {
        return selectedPitch != null ? selectedPitch.getBasePrice() : 0;
    }

    public double getSurcharge() {
        return (selectedSlot != null && selectedSlot.isPeakHour()) ? selectedSlot.getSurcharge() : 0;
    }

    public double getServicePrice() {
        double total = 0;
        for (Service s : selectedServices) total += s.getPrice();
        return total;
    }

    public String getFormattedBasePrice() {
        return currencyFormatter.format((long) getBasePrice()) + "đ";
    }

    public String getFormattedSurcharge() {
        return "+" + currencyFormatter.format((long) getSurcharge()) + "đ";
    }

    public String getFormattedServicePrice() {
        return "+" + currencyFormatter.format((long) getServicePrice()) + "đ";
    }

    // ============================================================
    // INIT: Thiết lập context (gọi từ Activity sau khi nhận Intent)
    // ============================================================

    public void loadInitialData(String pitchId) {
        if (pitchId == null) return;
        PitchRepository.getInstance().getPitchById(pitchId, pitchLiveData);
        AuthRepository.getInstance().checkCurrentUser(userLiveData);
    }

    public void setSelectedPitch(Pitch pitch) {
        this.selectedPitch = pitch;
        loadSlotsForDate(selectedDate);
        loadServices();
    }

    public void setCurrentUser(User user) {
        this.currentUser = user;
    }

    /** Preselects a date/slot coming from the availability browser. */
    public void preselectDateAndSlot(String date, String slotId) {
        if (date == null || date.trim().isEmpty()) return;
        String today = bookingDateFormat().format(new Date());
        if (date.compareTo(today) < 0) return;
        selectedDate = date;
        savedState.set("date", date);
        if (slotId == null || slotId.trim().isEmpty()) {
            savedStateRemoveSlot();
        } else {
            savedState.set("slotId", slotId);
        }
    }

    public void initBooking(Pitch pitch, User user) {
        this.selectedPitch = pitch;
        this.currentUser   = user;
        // Load slots cho ngày hôm nay
        loadSlotsForDate(selectedDate);
        loadServices();
    }

    // ============================================================
    // ACTIONS
    // ============================================================

    /** Tải danh sách dịch vụ đi kèm */
    public void loadServices() {
        final long generation = ++servicesGeneration;
        servicesLiveData.setValue(Resource.loading(null));
        com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection(Constants.COL_SERVICES)
                .whereEqualTo("isActive", true)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (generation != servicesGeneration) return;
                    List<Service> services = new ArrayList<>();
                    if (snapshot != null && !snapshot.isEmpty()) {
                        services = snapshot.toObjects(Service.class);
                    }
                    for (int i = 0; i < services.size(); i++) {
                        services.get(i).setServiceId(snapshot.getDocuments().get(i).getId());
                    }
                    List<Service> refreshedSelection = new ArrayList<>();
                    java.util.ArrayList<String> ids = savedState.get("serviceIds");
                    for (Service service : services) {
                        if (isServiceSelected(service) || (ids != null && ids.contains(service.getServiceId()))) refreshedSelection.add(service);
                    }
                    selectedServices.clear();
                    selectedServices.addAll(refreshedSelection);
                    saveServices();
                    recalculateTotal();
                    servicesLiveData.setValue(Resource.success(services));
                })
                .addOnFailureListener(e -> {
                    if (generation != servicesGeneration) return;
                    servicesLiveData.setValue(Resource.error("Không thể tải dịch vụ: " + e.getMessage(), null));
                });
    }

    /** Gọi khi user chọn ngày mới từ CalendarView */
    public void onDateSelected(long dateMillis) {
        String nextDate = bookingDateFormat().format(new Date(dateMillis));
        if (nextDate.equals(selectedDate)) return;
        selectedDate = nextDate;
        savedState.set("date", selectedDate);
        savedStateRemoveSlot();
        selectedSlot = null;         // Reset slot khi đổi ngày
        selectedServices.clear();    // Reset services
        saveServices();
        recalculateTotal();
        loadSlotsForDate(selectedDate);
    }

    /** Gọi khi user chọn/bỏ chọn 1 khung giờ */
    public void onSlotSelected(TimeSlot slot) {
        Resource<List<TimeSlot>> latest = availableSlots.getValue();
        if (slot == null || !slot.isAvailable() || latest == null || !latest.isSuccess()
                || latest.data == null || !latest.data.contains(slot)) return;
        if (slot.equals(selectedSlot)) {
            selectedSlot = null; // Toggle bỏ chọn
        } else {
            selectedSlot = slot;
        }
        savedState.set("slotId", selectedSlot == null ? null : selectedSlot.getSlotId());
        recalculateTotal();
    }

    /** Toggle service: thêm nếu chưa có, bỏ nếu đã có */
    public void toggleService(Service service, boolean isChecked) {
        if (service == null || service.getServiceId() == null) return;
        // Firestore reloads create new objects: identity is the document ID.
        selectedServices.removeIf(selected -> service.getServiceId().equals(selected.getServiceId()));
        if (isChecked) {
            selectedServices.add(service);
        }
        saveServices();
        recalculateTotal();
    }

    private void saveServices() {
        java.util.ArrayList<String> ids = new java.util.ArrayList<>();
        for (Service service : selectedServices) ids.add(service.getServiceId());
        savedState.set("serviceIds", ids);
    }

    public boolean isServiceSelected(Service service) {
        if (service == null || service.getServiceId() == null) return false;
        for (Service selected : selectedServices) {
            if (service.getServiceId().equals(selected.getServiceId())) return true;
        }
        return false;
    }

    /** Kiểm tra xem booking có thể xác nhận không */
    public boolean canConfirmBooking() {
        return getConfirmationError() == null;
    }

    public String getConfirmationError() {
        Resource<Booking> creation = createBookingResult.getValue();
        if (creation != null && creation.isLoading()) return "Đơn đang được gửi. Vui lòng chờ.";
        if (creation != null && creation.isSuccess()) return "Đơn đã được tạo. Hãy kiểm tra lịch sử đặt sân.";
        if (selectedPitch == null) return "Chưa tải được thông tin sân. Vui lòng mở lại màn hình.";
        if (selectedPitch.getBookingPolicyVersion() != 2) return "Lịch sân chưa được kiểm tra để mở đặt chỗ. Vui lòng liên hệ quản trị viên chuẩn bị lịch sân.";
        if (currentUser == null) return "Chưa tải được hồ sơ tài khoản. Vui lòng đăng nhập lại.";
        Resource<List<Service>> services = servicesLiveData.getValue();
        if (services == null || services.isLoading()) return "Đang tải dịch vụ. Vui lòng chờ.";
        if (services.isError()) return "Không tải được dịch vụ. Vui lòng mở lại màn hình để thử lại.";
        if (selectedDate == null || selectedSlot == null || !selectedSlot.isAvailable()) return "Vui lòng chọn một khung giờ còn trống.";
        return null;
    }

    /** Tạo đơn đặt sân với phương thức thanh toán chỉ định */
    public void confirmBooking(String note, String paymentMethod) {
        Resource<Booking> previous = createBookingResult.getValue();
        if (previous != null && (previous.isLoading() || previous.isSuccess())) return;
        String error = getConfirmationError();
        if (error != null) {
            createBookingResult.setValue(Resource.error(error, null));
            return;
        }
        if (!Constants.METHOD_CASH.equals(paymentMethod)
                && !Constants.METHOD_PAYOS.equals(paymentMethod)) {
            createBookingResult.setValue(Resource.error("Phương thức thanh toán không hợp lệ", null));
            return;
        }

        Booking booking = buildBookingObject(note, paymentMethod);
        bookingRepository.createBooking(booking, selectedPitch, createBookingResult);
    }

    /** Tạo đơn đặt sân (mặc định PayOS VietQR) */
    public void confirmBooking(String note) {
        confirmBooking(note, Constants.METHOD_PAYOS);
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
    private Booking buildBookingObject(String note, String paymentMethod) {
        Booking booking = new Booking();

        // Null-safety check cho currentUser
        if (currentUser == null) {
            com.google.firebase.auth.FirebaseUser fbUser = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
            if (fbUser != null) {
                currentUser = new User();
                currentUser.setUid(fbUser.getUid());
                currentUser.setDisplayName(fbUser.getDisplayName() != null && !fbUser.getDisplayName().isEmpty() ? fbUser.getDisplayName() : "Khách hàng");
                currentUser.setEmail(fbUser.getEmail() != null ? fbUser.getEmail() : "");
                currentUser.setPhoneNumber(fbUser.getPhoneNumber() != null ? fbUser.getPhoneNumber() : "");
            }
        }

        String customerUid = currentUser != null ? currentUser.getUid() : "";
        String customerName = currentUser != null && currentUser.getDisplayName() != null ? currentUser.getDisplayName() : "Khách hàng";
        String customerPhone = currentUser != null && currentUser.getPhoneNumber() != null ? currentUser.getPhoneNumber() : "";

        // Customer info (denormalized)
        booking.setCustomerId(customerUid);
        booking.setCustomerName(customerName);
        booking.setCustomerPhone(customerPhone);

        // Pitch info (denormalized)
        if (selectedPitch != null) {
            booking.setPitchId(selectedPitch.getPitchId());
            booking.setPitchName(selectedPitch.getName());
            booking.setPitchAddress(selectedPitch.getAddress());
            booking.setPitchType(selectedPitch.getType());
            booking.setBasePrice(selectedPitch.getBasePrice());
        }

        // Booking info
        booking.setBookingDate(selectedDate);
        if (selectedSlot != null) {
            booking.setSlotId(selectedSlot.getSlotId());
            booking.setStartTime(selectedSlot.getStartTime());
            booking.setEndTime(selectedSlot.getEndTime());
        }

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
        double surcharge = (selectedSlot != null && selectedSlot.isPeakHour()) ? selectedSlot.getSurcharge() : 0;
        booking.setSurcharge(surcharge);
        booking.setTotalAmount(totalAmount);

        // Payment Method & Note
        booking.setPaymentMethod(paymentMethod != null ? paymentMethod : Constants.METHOD_PAYOS);
        booking.setNote(note != null ? note : "");

        return booking;
    }

    private Booking buildBookingObject(String note) {
        return buildBookingObject(note, Constants.METHOD_PAYOS);
    }

    /** Kiểm tra đã qua giờ thi đấu chưa */
    private boolean isPastMatchTime(Booking booking) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
            sdf.setTimeZone(java.util.TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
            sdf.setLenient(false);
            Date matchDateTime = sdf.parse(booking.getBookingDate() + " " + booking.getStartTime());
            return matchDateTime != null && new Date().after(matchDateTime);
        } catch (Exception e) {
            return true;
        }
    }

    private static SimpleDateFormat bookingDateFormat() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        format.setTimeZone(java.util.TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
        return format;
    }

    public void detachSlotsListener() {
        if (bookingRepository != null) {
            bookingRepository.detachSlotsListener();
        }
    }

    @Override
    protected void onCleared() {
        availableSlots.removeObserver(slotsObserver);
        servicesGeneration++;
        super.onCleared();
        detachSlotsListener();
        bookingRepository.detachBookingListListener();
    }
}
