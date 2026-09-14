package com.example.footballbooking.data.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.ServerTimestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Model ánh xạ với Firestore collection "bookings".
 *
 * THIẾT KẾ QUAN TRỌNG:
 * - bookingStatus: luồng Admin duyệt (pending → approved/rejected/cancelled)
 * - matchStatus: trạng thái trận đấu thực tế (upcoming → playing → half_time → finished)
 * Hai trạng thái này hoàn toàn độc lập nhau.
 */
public class Booking {

    @DocumentId
    private String bookingId;

    // --- Thông tin khách hàng (Denormalized để tránh query join) ---
    private String customerId;
    private String customerName;
    private String customerPhone;

    // --- Thông tin sân (Denormalized) ---
    private String pitchId;
    private String pitchName;
    private String pitchAddress;
    private String pitchType;
    private String pitchOwnerId;    // ownerId của sân — dùng để Owner query đơn của mình

    // --- Thông tin lịch đặt ---
    private String bookingDate;  // Format: "yyyy-MM-dd", VD: "2025-06-15"
    private String slotId;
    private String startTime;    // "18:00"
    private String endTime;      // "19:30"

    // --- Dịch vụ kèm theo ---
    // Mỗi phần tử là Map gồm: {serviceId, serviceName, price}
    private List<Map<String, Object>> services;

    // --- Thanh toán ---
    private double basePrice;
    private double surcharge;           // Phụ phí giờ cao điểm
    private double totalServicePrice;
    private double totalAmount;         // = basePrice + surcharge + totalServicePrice
    private String paymentStatus;       // "unpaid", "paid", "refunded"
    private String paymentMethod;       // "vnpay", "cash"
    private String vnpayTransactionId;  // null nếu thanh toán tiền mặt

    // --- Trạng thái ---
    private String bookingStatus;   // "pending","approved","rejected","cancelled"
    private String matchStatus;     // "upcoming","playing","half_time","finished"

    // --- Ghi chú ---
    private String note;        // Ghi chú của khách
    private String adminNote;   // Lý do từ chối của Admin

    @ServerTimestamp
    private Timestamp createdAt;
    @ServerTimestamp
    private Timestamp updatedAt;

    // --- No-arg constructor bắt buộc ---
    public Booking() {
        this.services = new ArrayList<>();
    }

    // --- Getters & Setters ---
    public String getBookingId() { return bookingId; }
    public void setBookingId(String bookingId) { this.bookingId = bookingId; }

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public String getCustomerPhone() { return customerPhone; }
    public void setCustomerPhone(String customerPhone) { this.customerPhone = customerPhone; }

    public String getPitchId() { return pitchId; }
    public void setPitchId(String pitchId) { this.pitchId = pitchId; }

    public String getPitchName() { return pitchName; }
    public void setPitchName(String pitchName) { this.pitchName = pitchName; }

    public String getPitchAddress() { return pitchAddress; }
    public void setPitchAddress(String pitchAddress) { this.pitchAddress = pitchAddress; }

    public String getPitchType() { return pitchType; }
    public void setPitchType(String pitchType) { this.pitchType = pitchType; }

    public String getBookingDate() { return bookingDate; }
    public void setBookingDate(String bookingDate) { this.bookingDate = bookingDate; }

    public String getSlotId() { return slotId; }
    public void setSlotId(String slotId) { this.slotId = slotId; }

    public String getStartTime() { return startTime; }
    public void setStartTime(String startTime) { this.startTime = startTime; }

    public String getEndTime() { return endTime; }
    public void setEndTime(String endTime) { this.endTime = endTime; }

    public List<Map<String, Object>> getServices() { return services; }
    public void setServices(List<Map<String, Object>> services) { this.services = services; }

    public double getBasePrice() { return basePrice; }
    public void setBasePrice(double basePrice) { this.basePrice = basePrice; }

    public double getSurcharge() { return surcharge; }
    public void setSurcharge(double surcharge) { this.surcharge = surcharge; }

    public double getTotalServicePrice() { return totalServicePrice; }
    public void setTotalServicePrice(double totalServicePrice) { this.totalServicePrice = totalServicePrice; }

    public double getTotalAmount() { return totalAmount; }
    public void setTotalAmount(double totalAmount) { this.totalAmount = totalAmount; }

    public String getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(String paymentStatus) { this.paymentStatus = paymentStatus; }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    public String getVnpayTransactionId() { return vnpayTransactionId; }
    public void setVnpayTransactionId(String vnpayTransactionId) { this.vnpayTransactionId = vnpayTransactionId; }

    public String getBookingStatus() { return bookingStatus; }
    public void setBookingStatus(String bookingStatus) { this.bookingStatus = bookingStatus; }

    public String getMatchStatus() { return matchStatus; }
    public void setMatchStatus(String matchStatus) { this.matchStatus = matchStatus; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public String getAdminNote() { return adminNote; }
    public void setAdminNote(String adminNote) { this.adminNote = adminNote; }

    public String getPitchOwnerId() { return pitchOwnerId; }
    public void setPitchOwnerId(String pitchOwnerId) { this.pitchOwnerId = pitchOwnerId; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }

    // --- Utility methods ---
    /** Kiểm tra đơn có thể hủy không (chỉ hủy khi chưa được duyệt) */
    public boolean isCancellable() {
        return "pending".equals(bookingStatus) || "approved".equals(bookingStatus);
    }

    public boolean isPaid() {
        return "paid".equals(paymentStatus);
    }
}
