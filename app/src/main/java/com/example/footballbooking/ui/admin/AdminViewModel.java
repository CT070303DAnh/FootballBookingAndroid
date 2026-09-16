package com.example.footballbooking.ui.admin;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.footballbooking.data.model.Booking;
import com.example.footballbooking.data.model.Pitch;
import com.example.footballbooking.data.model.User;
import com.example.footballbooking.data.repository.AdminRepository;
import com.example.footballbooking.utils.Constants;
import com.example.footballbooking.utils.Resource;
import com.github.mikephil.charting.data.BarEntry;

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
    private final MutableLiveData<Resource<Boolean>>       actionResult    = new MutableLiveData<>();

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
    public MutableLiveData<Resource<Boolean>>       getActionResult()    { return actionResult; }
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
            if (b.getBookingDate() == null) continue;
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

    public double calcTotalRevenue(List<Booking> bookings) {
        if (bookings == null) return 0;
        double total = 0;
        for (Booking b : bookings) total += b.getTotalAmount();
        return total;
    }

    // ============================================================
    // USER MANAGEMENT
    // ============================================================

    public void loadAllUsers() {
        adminRepository.getAllUsers(allUsers);
    }

    public void updateUserStatus(String userId, String status) {
        adminRepository.updateUserStatus(userId, status, actionResult);
    }

    // ============================================================
    // PITCH MODERATION
    // ============================================================

    public void loadAllPitchesForModeration() {
        adminRepository.getAllPitchesForModeration(allPitches);
    }

    public void updatePitchStatus(String pitchId, String status) {
        adminRepository.updatePitchStatus(pitchId, status, actionResult);
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
