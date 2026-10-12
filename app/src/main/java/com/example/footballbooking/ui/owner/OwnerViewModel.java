package com.example.footballbooking.ui.owner;

import android.net.Uri;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.footballbooking.data.model.Booking;
import com.example.footballbooking.data.model.Pitch;
import com.example.footballbooking.data.model.User;
import com.example.footballbooking.data.repository.OwnerRepository;
import com.example.footballbooking.utils.Constants;
import com.example.footballbooking.utils.Resource;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * OwnerViewModel — Shared ViewModel cho toàn bộ Owner Dashboard.
 *
 * Scope: OwnerDashboardActivity → dùng chung giữa các Fragment.
 * Luôn filter theo currentOwner.uid để đảm bảo phân quyền.
 */
public class OwnerViewModel extends ViewModel {

    private final OwnerRepository ownerRepository;

    // Context của owner đang đăng nhập
    private User currentOwner;

    // LiveData
    private final MutableLiveData<Resource<List<Pitch>>>   myPitches      = new MutableLiveData<>();
    private final MutableLiveData<Resource<List<Booking>>> myBookings     = new MutableLiveData<>();
    private final MutableLiveData<Resource<List<Booking>>> reportBookings = new MutableLiveData<>();
    private final MutableLiveData<User> ownerReady = new MutableLiveData<>();
    private final MutableLiveData<Resource<Boolean>>       actionResult   = new MutableLiveData<>();
    private final MutableLiveData<Resource<String>>        uploadResult   = new MutableLiveData<>();
    private final MutableLiveData<Resource<Pitch>>         pitchCreateResult = new MutableLiveData<>();

    private String currentReportMonth;

    public OwnerViewModel() {
        ownerRepository = OwnerRepository.getInstance();
        currentReportMonth = new SimpleDateFormat("yyyy-MM", Locale.getDefault())
                .format(Calendar.getInstance().getTime());
    }

    // ============================================================
    // INIT
    // ============================================================

    /**
     * Phải gọi ngay sau khi OwnerDashboardActivity biết user là ai.
     */
    public void init(User owner) {
        this.currentOwner = owner;
        ownerReady.setValue(owner);
        loadMyPitches();
        loadMyBookings();
    }

    public User getCurrentOwner() { return currentOwner; }

    // ============================================================
    // EXPOSE LiveData
    // ============================================================

    public MutableLiveData<Resource<List<Pitch>>>   getMyPitches()         { return myPitches; }
    public MutableLiveData<Resource<List<Booking>>> getMyBookings()        { return myBookings; }
    public MutableLiveData<Resource<List<Booking>>> getReportBookings()    { return reportBookings; }
    public MutableLiveData<User> getOwnerReady()                            { return ownerReady; }
    public MutableLiveData<Resource<Boolean>>        getActionResult()     { return actionResult; }
    public MutableLiveData<Resource<String>>         getUploadResult()     { return uploadResult; }
    public MutableLiveData<Resource<Pitch>>          getPitchCreateResult(){ return pitchCreateResult; }
    public String getCurrentReportMonth()                                  { return currentReportMonth; }

    // ============================================================
    // PITCH MANAGEMENT
    // ============================================================

    public void loadMyPitches() {
        if (currentOwner == null) return;
        ownerRepository.getMyPitches(currentOwner.getUid(), myPitches);
    }

    public void createPitch(Pitch pitch) {
        if (currentOwner == null) return;
        ownerRepository.createMyPitch(pitch,
                currentOwner.getUid(),
                currentOwner.getDisplayName(),
                currentOwner.getPhoneNumber(),
                pitchCreateResult);
    }

    public void updatePitch(Pitch pitch) {
        if (currentOwner == null) return;
        ownerRepository.updateMyPitch(pitch, currentOwner.getUid(), actionResult);
    }

    public void closePitch(String pitchId) {
        if (currentOwner == null) return;
        ownerRepository.closeMyPitch(pitchId, currentOwner.getUid(), actionResult);
    }

    public void uploadPitchImage(String pitchId, Uri imageUri) {
        ownerRepository.uploadPitchImage(pitchId, imageUri, uploadResult);
    }

    // ============================================================
    // BOOKING MANAGEMENT
    // ============================================================

    public void loadMyBookings() {
        if (currentOwner == null) return;
        ownerRepository.getBookingsForMyPitches(currentOwner.getUid(), myBookings);
    }

    public void approveBooking(Booking booking) {
        if (currentOwner == null) return;
        ownerRepository.approveBooking(booking, currentOwner.getUid(), actionResult);
    }

    public void rejectBooking(Booking booking, String reason) {
        if (currentOwner == null) return;
        ownerRepository.rejectBooking(booking, reason, currentOwner.getUid(), actionResult);
    }

    public void updateMatchStatus(String bookingId, String matchStatus) {
        ownerRepository.updateMatchStatus(bookingId, matchStatus, actionResult);
    }

    public void confirmCashPayment(Booking booking) {
        if (currentOwner == null) return;
        ownerRepository.confirmCashPayment(booking, currentOwner.getUid(), actionResult);
    }

    // ============================================================
    // REPORT
    // ============================================================

    public void loadReportForMonth(String yearMonth) {
        if (currentOwner == null) return;
        currentReportMonth = yearMonth;
        ownerRepository.getMyBookingsByMonth(currentOwner.getUid(), yearMonth, reportBookings);
    }

    /** Gom doanh thu theo các tuần 1-7, 8-14... trong tháng đang xem. */
    public List<BarEntry> buildFiveDayRevenueBarEntries(List<Booking> bookings) {
        List<Integer> visibleBuckets = getRevenueBuckets(bookings);
        Map<Integer, Double> bucketRevenue = new HashMap<>();
        if (bookings != null) {
            for (Booking booking : bookings) {
                if (!isRevenueBooking(booking)) continue;
                try {
                    int day = Integer.parseInt(booking.getBookingDate().split("-")[2]);
                    if (day < 1 || day > getDaysInReportMonth()) continue;
                    int bucket = getFiveDayBucket(day);
                    bucketRevenue.put(bucket,
                            bucketRevenue.getOrDefault(bucket, 0.0) + booking.getTotalAmount());
                } catch (Exception ignored) {
                    // Bỏ qua dữ liệu ngày không hợp lệ.
                }
            }
        }

        List<BarEntry> entries = new ArrayList<>();
        for (int index = 0; index < visibleBuckets.size(); index++) {
            int bucket = visibleBuckets.get(index);
            entries.add(new BarEntry(index + 1,
                    (float) (bucketRevenue.getOrDefault(bucket, 0.0) / 1000)));
        }
        return entries;
    }

    /** Đếm đơn có doanh thu theo các tuần 1-7, 8-14... trong tháng đang xem. */
    public List<Entry> buildFiveDayBookingCountEntries(List<Booking> bookings) {
        List<Entry> entries = new ArrayList<>();
        List<Integer> visibleBuckets = getRevenueBuckets(bookings);
        int[] bucketCount = new int[getFiveDayBucketCount() + 1];

        if (bookings != null) {
            for (Booking booking : bookings) {
                // Chỉ đếm đơn tạo ra doanh thu: đã thanh toán online hoặc
                // đã được chủ sân xác nhận thu tiền mặt.
                if (!isRevenueBooking(booking)) continue;
                try {
                    int day = Integer.parseInt(booking.getBookingDate().split("-")[2]);
                    int bucket = getFiveDayBucket(day);
                    if (bucket >= 1 && bucket < bucketCount.length) bucketCount[bucket]++;
                } catch (Exception ignored) {
                    // Bỏ qua dữ liệu ngày không hợp lệ.
                }
            }
        }

        for (int index = 0; index < visibleBuckets.size(); index++) {
            entries.add(new Entry(index + 1, bucketCount[visibleBuckets.get(index)]));
        }
        return entries;
    }

    public List<String> buildFiveDayBucketLabels(List<Booking> bookings) {
        List<String> labels = new ArrayList<>();
        int daysInMonth = getDaysInReportMonth();
        for (int bucket : getRevenueBuckets(bookings)) {
            int startDay = ((bucket - 1) * 5) + 1;
            int endDay = bucket == 6 ? daysInMonth : Math.min(startDay + 4, daysInMonth);
            labels.add(startDay + "-" + endDay);
        }
        return labels;
    }

    private List<Integer> getRevenueBuckets(List<Booking> bookings) {
        // Giữ đủ các mốc của tháng để trục X luôn có nhãn ổn định.
        // Mốc không có doanh thu sẽ được biểu diễn bằng giá trị 0.
        List<Integer> buckets = new ArrayList<>();
        for (int bucket = 1; bucket <= getFiveDayBucketCount(); bucket++) {
            buckets.add(bucket);
        }
        return buckets;
    }

    private int getFiveDayBucketCount() {
        int daysInMonth = getDaysInReportMonth();
        return daysInMonth >= 26 ? 6 : (daysInMonth + 4) / 5;
    }

    private int getFiveDayBucket(int dayOfMonth) {
        // Mốc cuối luôn gộp từ ngày 26 đến ngày cuối tháng.
        return dayOfMonth >= 26 ? 6 : ((dayOfMonth - 1) / 5) + 1;
    }

    private int getDaysInReportMonth() {
        if (currentReportMonth == null) return 31;
        try {
            java.text.SimpleDateFormat format = new java.text.SimpleDateFormat(
                    "yyyy-MM", Locale.US);
            format.setLenient(false);
            java.util.Date date = format.parse(currentReportMonth);
            java.util.Calendar calendar = java.util.Calendar.getInstance();
            calendar.setTime(date);
            return calendar.getActualMaximum(java.util.Calendar.DAY_OF_MONTH);
        } catch (Exception ignored) {
            return 31;
        }
    }

    private boolean isRevenueBooking(Booking b) {
        return com.example.footballbooking.utils.RevenuePolicy.includes(b, currentReportMonth);
    }

    public double calcTotalRevenue(List<Booking> bookings) {
        if (bookings == null) return 0;
        double total = 0;
        for (Booking b : bookings) if (isRevenueBooking(b)) total += b.getTotalAmount();
        return total;
    }

    /** Tổng tiền đã thu từ trước đến nay của tất cả sân thuộc owner. */
    public double calcTotalRevenueAllTime(List<Booking> bookings) {
        if (bookings == null) return 0;
        double total = 0;
        for (Booking booking : bookings) {
            if (com.example.footballbooking.utils.RevenuePolicy.includesPaidBooking(booking)) {
                total += booking.getTotalAmount();
            }
        }
        return total;
    }

    /** Số đơn thực sự tạo ra doanh thu trong tháng đang xem. */
    public int countRevenueBookings(List<Booking> bookings) {
        if (bookings == null) return 0;
        int count = 0;
        for (Booking b : bookings) if (isRevenueBooking(b)) count++;
        return count;
    }

    /** Số đơn đã được duyệt nhưng vẫn chưa ghi nhận thanh toán. */
    public int countUnpaidApprovedBookings(List<Booking> bookings) {
        if (bookings == null) return 0;
        int count = 0;
        for (Booking b : bookings) {
            if (isInReportMonth(b)
                    && Constants.STATUS_APPROVED.equals(b.getBookingStatus())
                    && Constants.PAYMENT_UNPAID.equals(b.getPaymentStatus())) {
                count++;
            }
        }
        return count;
    }

    private boolean isInReportMonth(Booking booking) {
        return booking != null && currentReportMonth != null
                && booking.getBookingDate() != null
                && booking.getBookingDate().matches(
                    java.util.regex.Pattern.quote(currentReportMonth) + "-[0-9]{2}");
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        ownerRepository.detachAll();
    }
}
