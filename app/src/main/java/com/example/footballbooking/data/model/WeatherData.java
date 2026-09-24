package com.example.footballbooking.data.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * WeatherData — DTO ánh xạ JSON response từ OpenWeatherMap API.
 * Endpoint: GET /weather?lat={lat}&lon={lon}&appid={key}&units=metric&lang=vi
 *
 * JSON cấu trúc:
 * {
 *   "weather": [{"id": 800, "main": "Clear", "description": "trời quang"}],
 *   "main": {"temp": 32.5, "humidity": 75},
 *   "wind": {"speed": 3.5},
 *   "name": "Hanoi"
 * }
 */
public class WeatherData {

    @SerializedName("weather")
    private List<WeatherCondition> weather;

    @SerializedName("main")
    private MainData main;

    @SerializedName("wind")
    private Wind wind;

    @SerializedName("name")
    private String cityName;

    @SerializedName("cod")
    private int cod; // HTTP code: 200 = OK

    // --- No-arg constructor ---
    public WeatherData() {}

    // ============================================================
    // INNER CLASSES — ánh xạ nested JSON objects
    // ============================================================

    public static class WeatherCondition {
        @SerializedName("id")
        private int id; // Mã thời tiết: 800=clear, 500-504=rain, 200-232=thunder...

        @SerializedName("main")
        private String main; // "Clear", "Rain", "Clouds", "Thunderstorm"

        @SerializedName("description")
        private String description; // "trời quang", "mưa nhẹ"

        @SerializedName("icon")
        private String icon;

        public int getId() { return id; }
        public String getMain() { return main; }
        public String getDescription() { return description; }
        public String getIcon() { return icon; }

        /**
         * URL icon thời tiết đẹp từ OpenWeatherMap CDN.
         * Size: @2x = 100x100px, @4x = 200x200px
         */
        public String getIconUrl() {
            if (icon == null || icon.trim().isEmpty()) {
                return null;
            }
            return "https://openweathermap.org/img/wn/" + icon + "@2x.png";
        }
    }

    public static class MainData {
        @SerializedName("temp")
        private float temp; // Nhiệt độ Celsius (units=metric)

        @SerializedName("feels_like")
        private float feelsLike;

        @SerializedName("humidity")
        private int humidity; // %

        @SerializedName("pressure")
        private int pressure; // hPa

        public float getTemp() { return temp; }
        public float getFeelsLike() { return feelsLike; }
        public int getHumidity() { return humidity; }
        public int getPressure() { return pressure; }
    }

    public static class Wind {
        @SerializedName("speed")
        private float speed; // m/s

        @SerializedName("deg")
        private int deg; // Hướng gió (degrees)

        public float getSpeed() { return speed; }
        public int getDeg() { return deg; }
    }

    // ============================================================
    // GETTERS
    // ============================================================

    public List<WeatherCondition> getWeather() { return weather; }
    public MainData getMain() { return main; }
    public Wind getWind() { return wind; }
    public String getCityName() { return cityName; }
    public int getCod() { return cod; }

    // ============================================================
    // HELPER METHODS — dùng cho AI Scoring Engine
    // ============================================================

    /**
     * Lấy condition code đầu tiên (thường chỉ có 1).
     * 2xx = Thunderstorm, 3xx = Drizzle, 5xx = Rain,
     * 6xx = Snow, 7xx = Atmosphere, 800 = Clear, 80x = Clouds
     */
    public int getWeatherCode() {
        if (weather != null && !weather.isEmpty()) {
            return weather.get(0).getId();
        }
        return -1;
    }

    public String getMainCondition() {
        if (weather != null && !weather.isEmpty()) {
            return weather.get(0).getMain();
        }
        return "Unknown";
    }

    public String getDescription() {
        if (weather != null && !weather.isEmpty()) {
            return weather.get(0).getDescription();
        }
        return "";
    }

    public float getTemperature() {
        return main != null ? main.getTemp() : 0f;
    }

    public int getHumidity() {
        return main != null ? main.getHumidity() : 0;
    }

    public float getWindSpeed() {
        return wind != null ? wind.getSpeed() : 0f;
    }

    /**
     * Kiểm tra thời tiết có phù hợp để đá bóng không.
     * Trả về điểm từ 0.0 đến 1.0 (dùng cho AI Scoring).
     *
     * Logic:
     * - Clear sky (800): 1.0 — Hoàn hảo
     * - Few/scattered clouds (801-802): 0.9 — Tốt
     * - Overcast (803-804): 0.7 — Chấp nhận được
     * - Drizzle (300-321): 0.4 — Ít mưa, còn chơi được
     * - Rain (500-504): 0.1 — Không nên đá
     * - Heavy rain (502-504, 522): 0.0 — Nguy hiểm
     * - Thunderstorm (200-232): 0.0 — Cực kỳ nguy hiểm
     * - Temperature > 38°C hay < 15°C: giảm điểm
     */
    public float getWeatherScore() {
        int code = getWeatherCode();
        float score;

        if (code == 800)                        score = 1.0f;
        else if (code >= 801 && code <= 802)    score = 0.9f;
        else if (code >= 803 && code <= 804)    score = 0.7f;
        else if (code >= 300 && code <= 321)    score = 0.4f;
        else if (code == 500 || code == 501)    score = 0.2f;
        else if (code >= 502 && code <= 531)    score = 0.05f;
        else if (code >= 200 && code <= 232)    score = 0.0f;
        else                                     score = 0.5f; // Unknown

        // Điều chỉnh theo nhiệt độ
        float temp = getTemperature();
        if (temp > 38f)      score *= 0.7f; // Quá nóng
        else if (temp < 15f) score *= 0.8f; // Quá lạnh

        // Điều chỉnh theo gió mạnh
        if (getWindSpeed() > 10f) score *= 0.85f;

        return Math.max(0f, Math.min(1f, score)); // Clamp [0, 1]
    }

    /** Emoji mô tả thời tiết để hiển thị trên UI */
    public String getWeatherEmoji() {
        int code = getWeatherCode();
        if (code == 800)                        return "☀️";
        if (code >= 801 && code <= 802)         return "⛅";
        if (code >= 803 && code <= 804)         return "☁️";
        if (code >= 300 && code <= 321)         return "🌦️";
        if (code >= 500 && code <= 531)         return "🌧️";
        if (code >= 200 && code <= 232)         return "⛈️";
        if (code >= 600 && code <= 622)         return "❄️";
        return "🌡️";
    }
}
