package com.example.footballbooking.ui.admin;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.footballbooking.data.model.Booking;
import com.example.footballbooking.data.model.Pitch;
import com.example.footballbooking.data.model.User;
import com.example.footballbooking.data.remote.api.AdminUserApi;
import com.example.footballbooking.data.repository.AdminRepository;
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
 * AdminViewModel — Dùng chung cho các Fragment trong Admin Dashboard.
 */
public class AdminViewModel extends ViewModel {

    private final AdminRepository adminRepository;

    // ===== LiveData =====
    private final MutableLiveData<Resource<List<Booking>>> allBookings     = new MutableLiveData<>();
    private final MutableLiveData<Resource<List<User>>>    allUsers        = new MutableLiveData<>();
    private final MutableLiveData<Resource<List<Pitch>>>   allPitches      = new MutableLiveData<>();
    private final MutableLiveData<Resource<List<Booking>>> reportBookings  = new MutableLiveData<>();
    private final MutableLiveData<Resource<List<Booking>>> userHistory     = new MutableLiveData<>();
    private final MutableLiveData<Resource<Boolean>>       actionResult    = new MutableLiveData<>();
    private final MutableLiveData<Resource<Boolean>>       userCrudResult  = new MutableLiveData<>();

    // Tháng báo cáo hiện tại
    private String currentReportMonth;

    public AdminViewModel() {
        adminRepository = AdminRepository.getInstance();

        // Mặc định tháng hiện tại
        currentReportMonth = new SimpleDateFormat("yyyy-MM", Locale.getDefault())
                .format(Calendar.getInstance().getTime());
    }

    // ============================================================
    // EXPOSE
    // ============================================================

    public MutableLiveData<Resource<List<Booking>>> getAllBookings()     { return allBookings; }
    public MutableLiveData<Resource<List<User>>>    getAllUsers()        { return allUsers; }
    public MutableLiveData<Resource<List<Pitch>>>   getAllPitches()      { return allPitches; }
    public MutableLiveData<Resource<List<Booking>>> getReportBookings()  { return reportBookings; }
    public MutableLiveData<Resource<List<Booking>>> getUserHistory()     { return userHistory; }
    public MutableLiveData<Resource<Boolean>>       getActionResult()    { return actionResult; }
    public MutableLiveData<Resource<Boolean>>       getUserCrudResult()  { return userCrudResult; }
    public String getCurrentReportMonth()                                { return currentReportMonth; }

    // ============================================================
    // SYSTEM DASHBOARD & REPORT
    // ============================================================

    public void loadReportForMonth(String yearMonth) {
        currentReportMonth = yearMonth;
        adminRepository.getBookingsByMonth(yearMonth, reportBookings);
    }

    public void loadCurrentMonthReport() {
        loadReportForMonth(currentReportMonth);
    }

    public List<BarEntry> buildRevenueBarEntries(List<Booking> bookings) {
        if (bookings == null) return new ArrayList<>();

        Map<Integer, Double> dailyRevenue = new HashMap<>();
        for (Booking b : bookings) {
            if (!isRevenueBooking(b)) continue;
            try {
                String[] parts = b.getBookingDate().split("-");
                int day = Integer.parseInt(parts[2]);
                double current = dailyRevenue.getOrDefault(day, 0.0);
                dailyRevenue.put(day, current + b.getTotalAmount());
            } catch (Exception ignored) {}
        }

        List<BarEntry> entries = new ArrayList<>();
        for (Map.Entry<Integer, Double> e : dailyRevenue.entrySet()) {
            entries.add(new BarEntry(e.getKey(), (float)(e.getValue() / 1000)));
        }
        entries.sort((a, b) -> Float.compare(a.getX(), b.getX()));
        return entries;
    }

    public Map<String, Integer> buildStatusDistribution(List<Booking> bookings) {
        Map<String, Integer> dist = new HashMap<>();
        dist.put(Constants.STATUS_APPROVED,  0);
        dist.put(Constants.STATUS_PENDING,   0);
        dist.put(Constants.STATUS_REJECTED,  0);
        dist.put(Constants.STATUS_CANCELLED, 0);
        if (bookings == null) return dist;
        for (Booking b : bookings) {
            if (b.getBookingStatus() != null) {
                dist.put(b.getBookingStatus(),
                        dist.getOrDefault(b.getBookingStatus(), 0) + 1);
            }
        }
        return dist;
    }

    public boolean isRevenueBooking(Booking b) {
        return com.example.footballbooking.utils.RevenuePolicy.includes(b, currentReportMonth);
    }

    public double calcTotalRevenue(List<Booking> bookings) {
        if (bookings == null) return 0;
        double total = 0;
        for (Booking b : bookings) if (isRevenueBooking(b)) total += b.getTotalAmount();
        return total;
    }

    /** Số đơn đã thu tiền trong tháng báo cáo. */
    public int countRevenueBookings(List<Booking> bookings) {
        if (bookings == null) return 0;
        int count = 0;
        for (Booking booking : bookings) if (isRevenueBooking(booking)) count++;
        return count;
    }

    /** Gom doanh thu theo các mốc 1-5, 6-10, ..., 26-cuối tháng. */
    public List<BarEntry> buildFiveDayRevenueBarEntries(List<Booking> bookings) {
        List<Integer> visibleBuckets = getFiveDayBuckets();
        Map<Integer, Double> bucketRevenue = new HashMap<>();
        if (bookings != null) {
            for (Booking booking : bookings) {
                if (!isRevenueBooking(booking)) continue;
                int day = parseBookingDay(booking);
                if (day < 1 || day > getDaysInReportMonth()) continue;
                int bucket = getFiveDayBucket(day);
                bucketRevenue.put(bucket, bucketRevenue.getOrDefault(bucket, 0.0)
                        + booking.getTotalAmount());
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

    /** Đếm số đơn đã thu tiền theo cùng các mốc với biểu đồ doanh thu. */
    public List<Entry> buildFiveDayBookingCountEntries(List<Booking> bookings) {
        List<Integer> visibleBuckets = getFiveDayBuckets();
        int[] bucketCount = new int[getFiveDayBucketCount() + 1];
        if (bookings != null) {
            for (Booking booking : bookings) {
                if (!isRevenueBooking(booking)) continue;
                int day = parseBookingDay(booking);
                if (day < 1 || day > getDaysInReportMonth()) continue;
                int bucket = getFiveDayBucket(day);
                if (bucket >= 1 && bucket < bucketCount.length) bucketCount[bucket]++;
            }
        }
        List<Entry> entries = new ArrayList<>();
        for (int index = 0; index < visibleBuckets.size(); index++) {
            entries.add(new Entry(index + 1, bucketCount[visibleBuckets.get(index)]));
        }
        return entries;
    }

    public List<String> buildFiveDayBucketLabels() {
        List<String> labels = new ArrayList<>();
        int daysInMonth = getDaysInReportMonth();
        for (int bucket : getFiveDayBuckets()) {
            int startDay = ((bucket - 1) * 5) + 1;
            int endDay = bucket == 6 ? daysInMonth : Math.min(startDay + 4, daysInMonth);
            labels.add(startDay + "-" + endDay);
        }
        return labels;
    }

    private List<Integer> getFiveDayBuckets() {
        List<Integer> buckets = new ArrayList<>();
        for (int bucket = 1; bucket <= getFiveDayBucketCount(); bucket++) buckets.add(bucket);
        return buckets;
    }

    private int getFiveDayBucketCount() {
        int daysInMonth = getDaysInReportMonth();
        return daysInMonth >= 26 ? 6 : (daysInMonth + 4) / 5;
    }

    private int getFiveDayBucket(int dayOfMonth) {
        return dayOfMonth >= 26 ? 6 : ((dayOfMonth - 1) / 5) + 1;
    }

    private int parseBookingDay(Booking booking) {
        try {
            return Integer.parseInt(booking.getBookingDate().split("-")[2]);
        } catch (Exception ignored) {
            return -1;
        }
    }

    private int getDaysInReportMonth() {
        if (currentReportMonth == null) return 31;
        try {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM", Locale.US);
            format.setLenient(false);
            java.util.Date date = format.parse(currentReportMonth);
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(date);
            return calendar.getActualMaximum(Calendar.DAY_OF_MONTH);
        } catch (Exception ignored) {
            return 31;
        }
    }

    // ============================================================
    // USER MANAGEMENT
    // ============================================================

    public void loadAllUsers() {
        adminRepository.getAllUsers(allUsers);
    }

    public void updateUserStatus(String userId, String status, String reason) {
        if (actionResult.getValue() != null && actionResult.getValue().isLoading()) return;
        adminRepository.updateUserStatus(userId, status, reason, actionResult);
    }

    private final MutableLiveData<Resource<Boolean>> ownerApprovalResult = new MutableLiveData<>();

    public MutableLiveData<Resource<Boolean>> getOwnerApprovalResult() { return ownerApprovalResult; }

    public void approveOwner(String userId) {
        if (ownerApprovalResult.getValue() != null && ownerApprovalResult.getValue().isLoading()) return;
        adminRepository.approveOwner(userId, ownerApprovalResult);
    }

    public void rejectOwner(String userId, String reason) {
        if (ownerApprovalResult.getValue() != null && ownerApprovalResult.getValue().isLoading()) return;
        adminRepository.rejectOwner(userId, reason, ownerApprovalResult);
    }

    public void loadUserHistory(String userId) {
        adminRepository.getBookingsForUser(userId, userHistory);
    }

    public void clearActionResult() { actionResult.setValue(null); }

    public void createUser(AdminUserApi.UserPayload payload) {
        if (userCrudResult.getValue() != null && userCrudResult.getValue().isLoading()) return;
        adminRepository.createUser(payload, userCrudResult);
    }

    public void updateUser(String userId, AdminUserApi.UserPayload payload) {
        if (userCrudResult.getValue() != null && userCrudResult.getValue().isLoading()) return;
        adminRepository.updateUser(userId, payload, userCrudResult);
    }

    public void deleteUser(String userId) {
        if (userCrudResult.getValue() != null && userCrudResult.getValue().isLoading()) return;
        adminRepository.deleteUser(userId, userCrudResult);
    }

    public void clearUserCrudResult() { userCrudResult.setValue(null); }

    public void clearOwnerApprovalResult() { ownerApprovalResult.setValue(null); }

    // ============================================================
    // PITCH MODERATION
    // ============================================================

    public void loadAllPitchesForModeration() {
        adminRepository.getAllPitchesForModeration(allPitches);
    }

    public void moderatePitch(String pitchId, String moderationStatus, String reason) {
        if (actionResult.getValue() != null && actionResult.getValue().isLoading()) return;
        adminRepository.moderatePitch(pitchId, moderationStatus, reason, actionResult);
    }

    public void updatePitchDetails(Pitch pitch) {
        if (actionResult.getValue() != null && actionResult.getValue().isLoading()) return;
        adminRepository.updatePitchDetails(pitch, actionResult);
    }

    // ============================================================
    // BOOKING MONITORING (View Only)
    // ============================================================

    public void loadAllBookings() {
        adminRepository.getAllBookings(allBookings);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        adminRepository.detachAllBookingsListener();
        adminRepository.detachUsersListener();
        adminRepository.detachPitchesListener();
    }
}
