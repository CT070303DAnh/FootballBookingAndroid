package com.example.footballbooking.data.repository;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import androidx.lifecycle.MutableLiveData;
import com.example.footballbooking.BuildConfig;
import com.example.footballbooking.data.model.WeatherData;
import com.example.footballbooking.data.remote.RetrofitClient;
import com.example.footballbooking.data.remote.api.WeatherApiService;
import com.example.footballbooking.utils.Resource;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** One owner per repository. Public methods are called on the main thread. */
public class WeatherRepository {
    private final WeatherApiService weatherApiService;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private WeatherData cachedWeather;
    private String cachedKey;
    private long lastFetchTime;
    private long generation;
    private Call<WeatherData> activeCall;
    private static final long CACHE_DURATION_MS = 10 * 60 * 1000;

    private WeatherRepository() {
        weatherApiService = RetrofitClient.getInstance().getWeatherApiService();
    }

    public static WeatherRepository createScoped() { return new WeatherRepository(); }

    public void getWeatherByCoords(double latitude, double longitude,
                                   MutableLiveData<Resource<WeatherData>> result) {
        String key = "coords:" + Double.toString(latitude) + ":" + Double.toString(longitude);
        load(key, () -> weatherApiService.getCurrentWeatherByCoords(
                latitude, longitude, BuildConfig.OPENWEATHER_API_KEY, "metric", "vi"), result);
    }

    public void getWeatherHanoi(MutableLiveData<Resource<WeatherData>> result) {
        load("city:Hanoi,VN", () -> weatherApiService.getCurrentWeatherByCity(
                "Hanoi,VN", BuildConfig.OPENWEATHER_API_KEY, "metric", "vi"), result);
    }

    private void load(String key, java.util.concurrent.Callable<Call<WeatherData>> request,
                      MutableLiveData<Resource<WeatherData>> result) {
        cancelPending();
        final long requestGeneration = generation;
        if (cachedWeather != null && key.equals(cachedKey)
                && SystemClock.elapsedRealtime() - lastFetchTime < CACHE_DURATION_MS) {
            result.setValue(Resource.success(cachedWeather));
            return;
        }
        result.setValue(Resource.loading(null));
        try {
            activeCall = request.call();
            activeCall.enqueue(new Callback<WeatherData>() {
                @Override
                public void onResponse(Call<WeatherData> call, Response<WeatherData> response) {
                    mainHandler.post(() -> {
                        if (requestGeneration != generation) return;
                        activeCall = null;
                        if (response.isSuccessful() && response.body() != null) {
                            cachedWeather = response.body();
                            cachedKey = key;
                            lastFetchTime = SystemClock.elapsedRealtime();
                            result.setValue(Resource.success(cachedWeather));
                        } else {
                            result.setValue(Resource.error("API Error: " + response.code(), null));
                        }
                    });
                }
                @Override
                public void onFailure(Call<WeatherData> call, Throwable t) {
                    mainHandler.post(() -> {
                        if (requestGeneration != generation) return;
                        activeCall = null;
                        result.setValue(Resource.error("Không tải được thời tiết. Vui lòng thử lại.", null));
                    });
                }
            });
        } catch (Exception e) {
            if (requestGeneration == generation) {
                activeCall = null;
                result.setValue(Resource.error("Không tải được thời tiết. Vui lòng thử lại.", null));
            }
        }
    }

    public void cancelPending() {
        generation++;
        if (activeCall != null) activeCall.cancel();
        activeCall = null;
        mainHandler.removeCallbacksAndMessages(null);
    }

    public void clearCache() {
        cancelPending();
        cachedWeather = null;
        cachedKey = null;
        lastFetchTime = 0;
    }
}
