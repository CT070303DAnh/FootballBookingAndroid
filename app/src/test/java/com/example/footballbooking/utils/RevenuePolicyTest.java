package com.example.footballbooking.utils;

import com.example.footballbooking.data.model.Booking;
import org.junit.Test;
import static org.junit.Assert.*;

public class RevenuePolicyTest {
    private Booking booking(String payment, String status, String date, double amount) {
        return booking(payment, status, date, amount, Constants.METHOD_PAYOS);
    }

    private Booking booking(String payment, String status, String date, double amount,
                            String method) {
        Booking b = new Booking();
        b.setPaymentStatus(payment); b.setBookingStatus(status);
        b.setBookingDate(date); b.setTotalAmount(amount);
        b.setPaymentMethod(method);
        return b;
    }

    @Test public void includesPaidPayosTransfer() {
        assertTrue(RevenuePolicy.includes(
                booking(Constants.PAYMENT_PAID, Constants.STATUS_APPROVED,
                        "2026-10-09", 200000, Constants.METHOD_PAYOS), "2026-10"));
    }

    @Test public void includesCashAfterOwnerConfirmation() {
        assertTrue(RevenuePolicy.includes(
                booking(Constants.PAYMENT_PAID, Constants.STATUS_APPROVED,
                        "2026-10-09", 200000, Constants.METHOD_CASH), "2026-10"));
    }

    @Test public void excludesCashBeforeOwnerConfirmation() {
        assertFalse(RevenuePolicy.includes(
                booking(Constants.PAYMENT_UNPAID, Constants.STATUS_APPROVED,
                        "2026-10-09", 200000, Constants.METHOD_CASH), "2026-10"));
    }
    @Test public void includesPaidApprovedMatchInMonth() {
        assertTrue(RevenuePolicy.includes(booking("paid", "approved", "2026-10-09", 200000), "2026-10"));
    }
    @Test public void excludesUnpaidAndRefunded() {
        for (String payment : new String[]{"unpaid", "refunded", null})
            assertFalse(RevenuePolicy.includes(booking(payment, "approved", "2026-10-09", 200000), "2026-10"));
    }
    @Test public void excludesCancelledRejectedAndPending() {
        for (String status : new String[]{"cancelled", "rejected", "pending"})
            assertFalse(RevenuePolicy.includes(booking("paid", status, "2026-10-09", 200000), "2026-10"));
    }
    @Test public void excludesOtherMonthAndInvalidAmounts() {
        assertFalse(RevenuePolicy.includes(booking("paid", "approved", "2026-09-09", 200000), "2026-10"));
        for (double amount : new double[]{-1, Double.NaN, Double.POSITIVE_INFINITY})
            assertFalse(RevenuePolicy.includes(booking("paid", "approved", "2026-10-09", amount), "2026-10"));
    }
}
