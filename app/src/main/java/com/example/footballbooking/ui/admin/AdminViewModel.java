package com.example.footballbooking.ui.admin;

import android.net.Uri;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.footballbooking.data.model.Booking;
import com.example.footballbooking.data.model.Pitch;
import com.example.footballbooking.data.repository.AdminRepository;
import com.example.footballbooking.data.repository.PitchRepository;
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
 * Scope: AdminDashboardActivity → shared giữa BookingManagement, Report, PitchManagement.
 */
public class AdminViewModel extends ViewModel {

    private final AdminRepository adminRepository;
    private final PitchRepository pitchRepository;

    // ===== LiveData =====
    private final MutableLiveData<Resource<List<Booking>>> allBookings     = new MutableLiveData<>();
    private final MutableLiveData<Resource<List<Booking>>> pendingBookings = new MutableLiveData<>();
    private final MutableLiveData<Resource<List<Pitch>>>   allPitches      = new MutableLiveData<>();
    private final MutableLiveData<Resource<List<Booking>>> reportBookings  = new MutableLiveData<>();
    private final MutableLiveData<Resource<Boolean>>       actionResult    = new MutableLiveData<>();
    private final MutableLiveData<Resource<String>>        uploadResult    = new MutableLiveData<>();
    private final MutableLiveData<Resource<Pitch>>         pitchResult     = new MutableLiveData<>();

    // Tháng báo cáo hiện tại
    private String currentReportMonth;

    public AdminViewModel() {
        adminRepository = AdminRepository.getInstance();
        pitchRepository = PitchRepository.getInstance();

        // Mặc định tháng hiện tại
        currentReportMonth = new SimpleDateFormat("yyyy-MM", Locale.getDefault())
                .format(Calendar.getInstance().getTime());
    }

    // ============================================================
    // EXPOSE
    // ============================================================

    public MutableLiveData<Resource<List<Booking>>> getAllBookings()     { return allBookings; }
    public MutableLiveData<Resource<List<Booking>>> getPendingBookings() { return pendingBookings; }
    public MutableLiveData<Resource<List<Pitch>>>   getAllPitches()      { return allPitches; }
    public MutableLiveData<Resource<List<Booking>>> getReportBookings()  { return reportBookings; }
    public MutableLiveData<Resource<Boolean>>        getActionResult()   { return actionResult; }
    public MutableLiveData<Resource<String>>         getUploadResult()   { return uploadResult; }
    public MutableLiveData<Resource<Pitch>>          getPitchResult()    { return pitchResult; }
    public String getCurrentReportMonth()                                { return currentReportMonth; }

    // ============================================================
    // BOOKING MANAGEMENT
    // ============================================================

    public void loadAllBookings()     { adminRepository.getAllBookings(allBookings); }
    public void loadPendingBookings() { adminRepository.getPendingBookings(pendingBookings); }

    public void approveBooking(Booking booking) {
        adminRepository.approveBooking(booking, actionResult);
    }

    public void rejectBooking(Booking booking, String reason) {
        adminRepository.rejectBooking(booking, reason, actionResult);
    }

    public void updateMatchStatus(String bookingId, String matchStatus) {
        adminRepository.updateMatchStatus(bookingId, matchStatus, actionResult);
    }

    // ============================================================
    // PITCH MANAGEMENT
    // ============================================================

    public void loadAllPitches() { pitchRepository.getAllPitches(allPitches); }

    public void createPitch(Pitch pitch) { adminRepository.createPitch(pitch, pitchResult); }

    public void updatePitch(Pitch pitch) { adminRepository.updatePitch(pitch, actionResult); }

    public void deletePitch(String pitchId) { adminRepository.deletePitch(pitchId, actionResult); }

    public void uploadPitchImage(String pitchId, Uri imageUri) {
        adminRepository.uploadPitchImage(pitchId, imageUri, uploadResult);
    }

    // ============================================================
    // REPORT
    // ============================================================

    public void loadReportForMonth(String yearMonth) {
        currentReportMonth = yearMonth;
        adminRepository.getBookingsByMonth(yearMonth, reportBookings);
    }

    public void loadCurrentMonthReport() {
        loadReportForMonth(currentReportMonth);
    }

    /**
     * Tổng hợp doanh thu theo ngày trong tháng từ danh sách booking.
     * Trả về List<BarEntry> cho MPAndroidChart.
     *
     * @param bookings Danh sách booking trong tháng
     * @return List<BarEntry> — x = ngày (1-31), y = tổng doanh thu ngày đó (nghìn đồng)
     */
    public List<BarEntry> buildRevenueBarEntries(List<Booking> bookings) {
        if (bookings == null) return new ArrayList<>();

        // Map: ngày → tổng doanh thu
        Map<Integer, Double> dailyRevenue = new HashMap<>();
        for (Booking b : bookings) {
            if (b.getBookingDate() == null) continue;
            try {
                // "2025-06-15" → ngày 15
                String[] parts = b.getBookingDate().split("-");
                int day = Integer.parseInt(parts[2]);
                double current = dailyRevenue.getOrDefault(day, 0.0);
                dailyRevenue.put(day, current + b.getTotalAmount());
            } catch (Exception ignored) {}
        }

        List<BarEntry> entries = new ArrayList<>();
        for (Map.Entry<Integer, Double> e : dailyRevenue.entrySet()) {
            // Chia 1000 để hiển thị đơn vị nghìn đồng
            entries.add(new BarEntry(e.getKey(), (float)(e.getValue() / 1000)));
        }
        // Sort theo ngày
        entries.sort((a, b) -> Float.compare(a.getX(), b.getX()));
        return entries;
    }

    /**
     * Thống kê tỷ lệ trạng thái booking cho PieChart.
     * Trả về Map: status → count
     */
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

    /**
     * Tính tổng doanh thu từ danh sách booking.
     */
    public double calcTotalRevenue(List<Booking> bookings) {
        if (bookings == null) return 0;
        double total = 0;
        for (Booking b : bookings) total += b.getTotalAmount();
        return total;
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        adminRepository.detachAllBookingsListener();
        adminRepository.detachPendingListener();
        pitchRepository.detachPitchListListener();
    }
}
