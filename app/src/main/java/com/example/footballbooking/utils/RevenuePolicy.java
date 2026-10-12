package com.example.footballbooking.utils;

import com.example.footballbooking.data.model.Booking;

/**
 * Chính sách doanh thu theo tháng.
 *
 * Doanh thu được ghi nhận khi đơn đã được duyệt và paymentStatus = paid.
 * Vì vậy policy này bao phủ cả PayOS/VietQR đã nhận tiền và tiền mặt đã
 * được chủ sân xác nhận; mọi đơn unpaid/refunded đều bị loại.
 */
public final class RevenuePolicy {
    private RevenuePolicy() { }

    /**
     * Kiểm tra một booking có được ghi nhận vào doanh thu hay không,
     * không phụ thuộc tháng báo cáo.
     */
    public static boolean includesPaidBooking(Booking booking) {
        return booking != null
                && Constants.PAYMENT_PAID.equals(booking.getPaymentStatus())
                && Constants.STATUS_APPROVED.equals(booking.getBookingStatus())
                && Double.isFinite(booking.getTotalAmount())
                && booking.getTotalAmount() >= 0;
    }

    public static boolean includes(Booking booking, String month) {
        return includesPaidBooking(booking) && month != null
                && booking.getBookingDate() != null
                && booking.getBookingDate().matches(java.util.regex.Pattern.quote(month) + "-[0-9]{2}")
                ;
    }
}
