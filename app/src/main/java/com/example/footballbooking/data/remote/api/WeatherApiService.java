package com.example.footballbooking.data.remote.api;

import com.example.footballbooking.data.model.WeatherData;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

/**
 * WeatherApiService — Retrofit interface cho OpenWeatherMap API.
 *
 * BASE URL: https://api.openweathermap.org/data/2.5/
 *
 * Endpoint sử dụng: /weather (current weather)
 * Docs: https://openweathermap.org/current
 *
 * Query params:
 * - lat, lon: tọa độ GPS
 * - appid: API Key từ BuildConfig
 * - units: "metric" → nhiệt độ Celsius
 * - lang: "vi" → mô tả tiếng Việt
 */
public interface WeatherApiService {

    /**
     * Lấy thời tiết hiện tại theo tọa độ GPS.
     * Dùng cho tính năng AI gợi ý sân.
     */
    @GET("weather")
    Call<WeatherData> getCurrentWeatherByCoords(
            @Query("lat") double latitude,
            @Query("lon") double longitude,
            @Query("appid") String apiKey,
            @Query("units") String units,       // "metric"
            @Query("lang") String lang          // "vi"
    );

    /**
     * Lấy thời tiết theo tên thành phố (fallback nếu không có GPS).
     */
    @GET("weather")
    Call<WeatherData> getCurrentWeatherByCity(
            @Query("q") String cityName,         // "Hanoi,VN"
            @Query("appid") String apiKey,
            @Query("units") String units,
            @Query("lang") String lang
    );
}
