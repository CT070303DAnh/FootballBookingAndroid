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

    public static boolean includes(Booking booking, String month) {
        return booking != null && month != null
                && "paid".equals(booking.getPaymentStatus())
                && Constants.STATUS_APPROVED.equals(booking.getBookingStatus())
                && booking.getBookingDate() != null
                && booking.getBookingDate().matches(java.util.regex.Pattern.quote(month) + "-[0-9]{2}")
                && Double.isFinite(booking.getTotalAmount()) && booking.getTotalAmount() >= 0;
    }
}
