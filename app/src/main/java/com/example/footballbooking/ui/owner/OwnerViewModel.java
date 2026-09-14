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

    // ============================================================
    // REPORT
    // ============================================================

    public void loadReportForMonth(String yearMonth) {
        if (currentOwner == null) return;
        currentReportMonth = yearMonth;
        ownerRepository.getMyBookingsByMonth(currentOwner.getUid(), yearMonth, reportBookings);
    }

    /** Build BarEntry list từ danh sách booking (tái sử dụng logic từ AdminViewModel) */
    public List<BarEntry> buildRevenueBarEntries(List<Booking> bookings) {
        if (bookings == null) return new ArrayList<>();
        Map<Integer, Double> dailyRevenue = new HashMap<>();
        for (Booking b : bookings) {
            if (b.getBookingDate() == null) continue;
            try {
                int day = Integer.parseInt(b.getBookingDate().split("-")[2]);
                dailyRevenue.put(day, dailyRevenue.getOrDefault(day, 0.0) + b.getTotalAmount());
            } catch (Exception ignored) {}
        }
        List<BarEntry> entries = new ArrayList<>();
        for (Map.Entry<Integer, Double> e : dailyRevenue.entrySet()) {
            entries.add(new BarEntry(e.getKey(), (float)(e.getValue() / 1000)));
        }
        entries.sort((a, b) -> Float.compare(a.getX(), b.getX()));
        return entries;
    }

    public double calcTotalRevenue(List<Booking> bookings) {
        if (bookings == null) return 0;
        double total = 0;
        for (Booking b : bookings) total += b.getTotalAmount();
        return total;
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        ownerRepository.detachAll();
    }
}
