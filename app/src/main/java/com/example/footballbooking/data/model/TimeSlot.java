package com.example.footballbooking.data.model;

import com.google.firebase.firestore.DocumentId;

/**
 * Model ánh xạ với sub-collection "timeSlots" của pitches.
 * Mỗi sân có nhiều khung giờ, mỗi khung giờ có thể là giờ cao điểm.
 */
public class TimeSlot {

    @DocumentId
    private String slotId;
    private String startTime;   // "06:00"
    private String endTime;     // "07:30"
    private boolean isPeakHour; // true = giờ cao điểm
    private double surcharge;   // Phụ phí VND nếu isPeakHour = true
    private boolean isActive;

    // --- No-arg constructor bắt buộc ---
    public TimeSlot() {}

    public TimeSlot(String startTime, String endTime,
                    boolean isPeakHour, double surcharge) {
        this.startTime = startTime;
        this.endTime = endTime;
        this.isPeakHour = isPeakHour;
        this.surcharge = surcharge;
        this.isActive = true;
    }

    // --- Getters & Setters ---
    public String getSlotId() { return slotId; }
    public void setSlotId(String slotId) { this.slotId = slotId; }

    public String getStartTime() { return startTime; }
    public void setStartTime(String startTime) { this.startTime = startTime; }

    public String getEndTime() { return endTime; }
    public void setEndTime(String endTime) { this.endTime = endTime; }

    public boolean isPeakHour() { return isPeakHour; }
    public void setPeakHour(boolean peakHour) { isPeakHour = peakHour; }

    public double getSurcharge() { return surcharge; }
    public void setSurcharge(double surcharge) { this.surcharge = surcharge; }

    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }

    /** Trả về label đẹp cho UI, VD: "06:00 - 07:30 ⚡ Giờ cao điểm" */
    public String getDisplayLabel() {
        String base = startTime + " - " + endTime;
        return isPeakHour ? base + " ⚡ Cao điểm" : base;
    }
}
