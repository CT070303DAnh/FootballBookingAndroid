package com.example.footballbooking.utils;

import android.location.Location;

import com.example.footballbooking.data.model.Pitch;
import com.example.footballbooking.data.model.WeatherData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * AiScoringEngine — Thuật toán gợi ý sân thông minh bằng Weighted Scoring.
 *
 * ═══════════════════════════════════════════════════════════
 * CÔNG THỨC TỔNG QUÁT:
 * AI_Score = W_distance × S_distance
 *          + W_weather  × S_weather
 *          + W_rating   × S_rating
 *          + W_history  × S_history
 *
 * Trong đó:
 * - W_* là trọng số (tổng = 1.0, xem Constants.java)
 * - S_* là điểm thành phần, chuẩn hóa về [0.0, 1.0]
 * ═══════════════════════════════════════════════════════════
 *
 * TRỌNG SỐ:
 * ┌────────────────┬──────────┬───────────────────────────────────┐
 * │ Yếu tố        │ Trọng số │ Lý do                             │
 * ├────────────────┼──────────┼───────────────────────────────────┤
 * │ Khoảng cách   │  30%     │ User ưu tiên sân gần nhà          │
 * │ Thời tiết     │  25%     │ Thời tiết ảnh hưởng trải nghiệm   │
 * │ Rating        │  25%     │ Chất lượng sân quan trọng          │
 * │ Lịch sử       │  20%     │ Sân quen → tâm lý thoải mái        │
 * └────────────────┴──────────┴───────────────────────────────────┘
 */
public class AiScoringEngine {

    /**
     * Kết quả gợi ý cho 1 sân — bao gồm điểm số và lý giải.
     */
    public static class ScoredPitch {
        public final Pitch pitch;
        public final float totalScore;      // [0.0, 1.0]
        public final float distanceScore;   // điểm thành phần
        public final float weatherScore;
        public final float ratingScore;
        public final float historyScore;
        public final float distanceKm;      // khoảng cách thực tế (km)
        public final String recommendation; // Lý giải bằng tiếng Việt

        public ScoredPitch(Pitch pitch, float total, float dist, float weather,
                           float rating, float history, float distanceKm,
                           String recommendation) {
            this.pitch         = pitch;
            this.totalScore    = total;
            this.distanceScore = dist;
            this.weatherScore  = weather;
            this.ratingScore   = rating;
            this.historyScore  = history;
            this.distanceKm    = distanceKm;
            this.recommendation = recommendation;
        }

        /** Điểm % để hiển thị trên UI */
        public int getScorePercent() {
            return Math.round(totalScore * 100);
        }
    }

    // ============================================================
    // METHOD CHÍNH: Tính điểm và xếp hạng tất cả sân
    // ============================================================

    /**
     * Xếp hạng sân theo AI Score.
     *
     * @param pitches         Danh sách sân cần xếp hạng
     * @param userLocation    Vị trí GPS của user (có thể null)
     * @param weather         Dữ liệu thời tiết hiện tại (có thể null)
     * @param bookingHistory  Map: pitchId → số lần user đã đặt sân này
     * @return                Danh sách ScoredPitch đã sắp xếp giảm dần theo score
     */
    public static List<ScoredPitch> rankPitches(
            List<Pitch> pitches,
            Location userLocation,
            WeatherData weather,
            Map<String, Integer> bookingHistory) {

        if (pitches == null || pitches.isEmpty()) return new ArrayList<>();

        // Tính max booking count để chuẩn hóa history score
        int maxBookingCount = 1;
        if (bookingHistory != null && !bookingHistory.isEmpty()) {
            for (int count : bookingHistory.values()) {
                if (count > maxBookingCount) maxBookingCount = count;
            }
        }

        List<ScoredPitch> results = new ArrayList<>();
        float weatherScore = weather != null ? weather.getWeatherScore() : 0.5f;

        for (Pitch pitch : pitches) {
            // Bỏ qua sân đang bảo trì
            if (Constants.PITCH_MAINTENANCE.equals(pitch.getStatus())) continue;

            // 1. Distance Score
            float distKm      = calcDistanceKm(pitch, userLocation);
            float distScore   = calcDistanceScore(distKm, userLocation);

            // 2. Weather Score (áp dụng như nhau cho tất cả sân)
            // Sân có mái che → bonus thời tiết
            float adjWeatherScore = weatherScore;
            if (hasCover(pitch)) {
                adjWeatherScore = Math.min(1.0f, weatherScore + 0.2f); // Bonus +20%
            }

            // 3. Rating Score — chuẩn hóa từ [0-5] → [0-1]
            float ratingScore = pitch.getRating() / 5.0f;

            // 4. History Score — user đã đặt bao nhiêu lần
            int bookCount = 0;
            if (bookingHistory != null && pitch.getPitchId() != null) {
                bookCount = bookingHistory.getOrDefault(pitch.getPitchId(), 0);
            }
            float histScore = maxBookingCount > 0
                    ? (float) bookCount / maxBookingCount : 0f;

            // 5. TỔNG HỢP ĐIỂM với trọng số
            float totalScore = Constants.WEIGHT_DISTANCE * distScore
                    + Constants.WEIGHT_WEATHER  * adjWeatherScore
                    + Constants.WEIGHT_RATING   * ratingScore
                    + Constants.WEIGHT_HISTORY  * histScore;

            // Clamp [0, 1]
            totalScore = Math.max(0f, Math.min(1f, totalScore));

            // Tạo lý giải tự động
            String recommendation = buildRecommendation(
                    pitch, distKm, adjWeatherScore, ratingScore,
                    histScore, weather, bookCount);

            results.add(new ScoredPitch(
                    pitch, totalScore,
                    distScore, adjWeatherScore, ratingScore, histScore,
                    distKm, recommendation
            ));
        }

        // Sắp xếp giảm dần theo tổng điểm
        Collections.sort(results, (a, b) -> Float.compare(b.totalScore, a.totalScore));

        return results;
    }

    // ============================================================
    // PRIVATE HELPERS
    // ============================================================

    /** Tính khoảng cách km từ user đến sân */
    private static float calcDistanceKm(Pitch pitch, Location userLocation) {
        if (userLocation == null || pitch.getLocation() == null) return 999f;
        float[] results = new float[1];
        Location.distanceBetween(
                userLocation.getLatitude(), userLocation.getLongitude(),
                pitch.getLocation().getLatitude(), pitch.getLocation().getLongitude(),
                results);
        return results[0] / 1000f;
    }

    /**
     * Chuyển khoảng cách (km) → điểm [0, 1].
     *
     * Logic phi tuyến (dùng hàm mũ):
     * 0 km  → 1.0  (gần nhất, điểm tuyệt đối)
     * 2 km  → 0.82
     * 5 km  → 0.61
     * 10 km → 0.37
     * 20 km → 0.14
     * ≥50 km → ≈ 0.0
     */
    private static float calcDistanceScore(float distKm, Location userLocation) {
        if (userLocation == null) return 0.5f; // Không có GPS → điểm trung bình
        // Công thức: score = e^(-0.05 × distance)
        return (float) Math.exp(-0.05 * distKm);
    }

    /** Kiểm tra sân có mái che không */
    private static boolean hasCover(Pitch pitch) {
        if (pitch.getAmenities() == null) return false;
        for (String amenity : pitch.getAmenities()) {
            if (amenity != null && amenity.toLowerCase().contains("mái")) return true;
        }
        return false;
    }

    /**
     * Tự động tạo câu lý giải bằng tiếng Việt cho UI.
     * Mỗi sân sẽ có 1 câu giải thích ngắn gọn tại sao được gợi ý.
     */
    private static String buildRecommendation(
            Pitch pitch, float distKm, float weatherScore,
            float ratingScore, float histScore,
            WeatherData weather, int bookCount) {

        StringBuilder sb = new StringBuilder();

        // Thời tiết
        if (weather != null) {
            sb.append(weather.getWeatherEmoji()).append(" ")
              .append(weather.getDescription()).append(", ");
        }

        // Khoảng cách
        if (distKm < 1f) {
            sb.append("chỉ ").append(Math.round(distKm * 1000)).append("m từ bạn");
        } else if (distKm < 5f) {
            sb.append(String.format("%.1f km từ bạn", distKm));
        } else {
            sb.append(String.format("%.0f km - hơi xa", distKm));
        }

        // Rating
        if (ratingScore >= 0.9f) {
            sb.append(", ⭐ Rating xuất sắc ").append(pitch.getRating());
        } else if (ratingScore >= 0.7f) {
            sb.append(", ⭐ Rating tốt ").append(pitch.getRating());
        }

        // Lịch sử
        if (bookCount > 0) {
            sb.append(", bạn đã đặt ").append(bookCount).append(" lần");
        }

        // Mái che + thời tiết xấu
        if (hasCover(pitch) && weatherScore < 0.5f) {
            sb.append(" 🏠 (có mái che, phù hợp thời tiết này)");
        }

        return sb.toString();
    }
}
