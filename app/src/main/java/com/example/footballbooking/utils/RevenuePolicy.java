package com.example.footballbooking.utils;

import com.example.footballbooking.data.model.Booking;

/** Revenue by match month, excluding unpaid, refunded and cancelled orders. */
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
