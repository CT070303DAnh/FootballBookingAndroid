package com.example.footballbooking.utils;

/**
 * Tập trung toàn bộ hằng số của ứng dụng.
 * Không bao giờ hardcode string trực tiếp trong code — luôn dùng class này.
 */
public final class Constants {

    private Constants() {} // Ngăn khởi tạo instance

    // ===== FIRESTORE COLLECTIONS =====
    public static final String COL_USERS         = "users";
    public static final String COL_PITCHES        = "pitches";
    public static final String COL_BOOKINGS       = "bookings";
    public static final String COL_SERVICES       = "services";
    public static final String COL_REVIEWS        = "reviews";
    public static final String COL_EMPLOYEES      = "employees";
    public static final String COL_NOTIFICATIONS  = "notifications";

    // Sub-collections
    public static final String SUB_TIME_SLOTS      = "timeSlots";
    public static final String SUB_AVAILABILITY    = "availability";
    public static final String SUB_BOOKING_HISTORY = "bookingHistory";

    // ===== USER ROLES =====
    public static final String ROLE_CUSTOMER      = "customer";
    public static final String ROLE_ADMIN         = "admin";
    public static final String ROLE_OWNER         = "owner";         // Chủ sân
    public static final String ROLE_OWNER_PENDING = "owner_pending"; // Chủ sân chờ Admin duyệt

    // ===== BOOKING STATUS =====
    public static final String STATUS_PENDING   = "pending";
    public static final String STATUS_APPROVED  = "approved";
    public static final String STATUS_REJECTED  = "rejected";
    public static final String STATUS_CANCELLED = "cancelled";

    // ===== MATCH STATUS =====
    public static final String MATCH_UPCOMING  = "upcoming";
    public static final String MATCH_PLAYING   = "playing";
    public static final String MATCH_HALF_TIME = "half_time";
    public static final String MATCH_FINISHED  = "finished";

    // ===== PAYMENT =====
    public static final String PAYMENT_UNPAID   = "unpaid";
    public static final String PAYMENT_PAID     = "paid";
    public static final String PAYMENT_REFUNDED = "refunded";
    public static final String METHOD_VNPAY     = "vnpay";
    public static final String METHOD_CASH      = "cash";
    public static final String METHOD_PAY_LATER = "pay_later"; // Thanh toán sau


    // ===== PITCH TYPE =====
    public static final String PITCH_TYPE_5  = "5";
    public static final String PITCH_TYPE_7  = "7";
    public static final String PITCH_TYPE_11 = "11";

    // ===== PITCH STATUS =====
    public static final String PITCH_AVAILABLE   = "available";
    public static final String PITCH_MAINTENANCE = "maintenance";
    public static final String PITCH_CLOSED      = "closed";

    // ===== API BASE URLs =====
    public static final String WEATHER_BASE_URL = "https://api.openweathermap.org/data/2.5/";
    public static final String VNPAY_BASE_URL   = "https://sandbox.vnpayment.vn/";

    // ===== SHARED PREFERENCES =====
    public static final String PREF_NAME      = "football_booking_prefs";
    public static final String PREF_USER_ROLE = "user_role";

    // ===== INTENT EXTRAS =====
    public static final String EXTRA_PITCH_ID   = "extra_pitch_id";
    public static final String EXTRA_BOOKING_ID = "extra_booking_id";
    public static final String EXTRA_USER_ROLE  = "extra_user_role";
    public static final String EXTRA_OWNER_ID   = "extra_owner_id";

    // ===== NOTIFICATION TYPES =====
    public static final String NOTIF_BOOKING_APPROVED = "booking_approved";
    public static final String NOTIF_BOOKING_REJECTED = "booking_rejected";
    public static final String NOTIF_REMINDER         = "reminder";

    // ===== AI SCORING WEIGHTS (tổng = 1.0) =====
    public static final float WEIGHT_DISTANCE = 0.30f; // Khoảng cách GPS
    public static final float WEIGHT_WEATHER  = 0.25f; // Thời tiết phù hợp
    public static final float WEIGHT_RATING   = 0.25f; // Điểm đánh giá sân
    public static final float WEIGHT_HISTORY  = 0.20f; // Lịch sử đặt sân của user
}
