package com.example.footballbooking.data.repository;

import androidx.lifecycle.MutableLiveData;

import com.example.footballbooking.BuildConfig;
import com.example.footballbooking.data.model.WeatherData;
import com.example.footballbooking.data.remote.RetrofitClient;
import com.example.footballbooking.data.remote.api.WeatherApiService;
import com.example.footballbooking.utils.Resource;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * WeatherRepository — Lấy dữ liệu thời tiết qua Retrofit.
 *
 * Cache đơn giản: lưu kết quả trong memory để tránh gọi API nhiều lần
 * khi user navigate qua lại các màn hình.
 * Cache hết hạn sau 10 phút.
 */
public class WeatherRepository {

    private final WeatherApiService weatherApiService;

    // Simple in-memory cache
    private WeatherData cachedWeather;
    private long lastFetchTime = 0;
    private static final long CACHE_DURATION_MS = 10 * 60 * 1000; // 10 phút

    private static WeatherRepository instance;

    private WeatherRepository() {
        weatherApiService = RetrofitClient.getInstance().getWeatherApiService();
    }

    public static synchronized WeatherRepository getInstance() {
        if (instance == null) instance = new WeatherRepository();
        return instance;
    }

    // ============================================================
    // LẤY THỜI TIẾT THEO GPS
    // ============================================================

    /**
     * Lấy thời tiết tại tọa độ GPS.
     * Sử dụng cache nếu data còn mới (< 10 phút).
     *
     * @param latitude  Vĩ độ
     * @param longitude Kinh độ
     * @param result    LiveData nhận kết quả
     */
    public void getWeatherByCoords(double latitude, double longitude,
                                   MutableLiveData<Resource<WeatherData>> result) {

        // Kiểm tra cache còn hợp lệ không
        if (cachedWeather != null
                && System.currentTimeMillis() - lastFetchTime < CACHE_DURATION_MS) {
            result.setValue(Resource.success(cachedWeather));
            return;
        }

        result.setValue(Resource.loading(null));

        weatherApiService.getCurrentWeatherByCoords(
                latitude,
                longitude,
                BuildConfig.OPENWEATHER_API_KEY,
                "metric",
                "vi"
        ).enqueue(new Callback<WeatherData>() {

            @Override
            public void onResponse(Call<WeatherData> call, Response<WeatherData> response) {
                if (response.isSuccessful() && response.body() != null) {
                    cachedWeather = response.body();
                    lastFetchTime = System.currentTimeMillis();
                    result.postValue(Resource.success(cachedWeather));
                } else {
                    result.postValue(Resource.error(
                            "API Error: " + response.code(), null));
                }
            }

            @Override
            public void onFailure(Call<WeatherData> call, Throwable t) {
                result.postValue(Resource.error(
                        "Lỗi kết nối: " + t.getMessage(), null));
            }
        });
    }

    /** Fallback: lấy thời tiết Hà Nội khi không có GPS */
    public void getWeatherHanoi(MutableLiveData<Resource<WeatherData>> result) {
        result.setValue(Resource.loading(null));
        weatherApiService.getCurrentWeatherByCity(
                "Hanoi,VN",
                BuildConfig.OPENWEATHER_API_KEY,
                "metric", "vi"
        ).enqueue(new Callback<WeatherData>() {
            @Override
            public void onResponse(Call<WeatherData> call, Response<WeatherData> response) {
                if (response.isSuccessful() && response.body() != null) {
                    result.postValue(Resource.success(response.body()));
                } else {
                    result.postValue(Resource.error("Lỗi API thời tiết", null));
                }
            }
            @Override
            public void onFailure(Call<WeatherData> call, Throwable t) {
                result.postValue(Resource.error(t.getMessage(), null));
            }
        });
    }

    /** Xóa cache thủ công (dùng khi muốn force refresh) */
    public void clearCache() {
        cachedWeather = null;
        lastFetchTime = 0;
    }
}
