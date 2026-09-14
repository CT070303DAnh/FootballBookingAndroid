package com.example.footballbooking.data.remote;

import com.example.footballbooking.data.remote.api.WeatherApiService;
import com.example.footballbooking.utils.Constants;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import java.util.concurrent.TimeUnit;

/**
 * RetrofitClient — Singleton factory tạo Retrofit instances.
 *
 * THIẾT KẾ:
 * - 1 OkHttpClient dùng chung (connection pooling)
 * - Logging interceptor chỉ bật ở DEBUG build
 * - Timeout 15s để tránh treo app khi mạng chậm
 */
public class RetrofitClient {

    private static RetrofitClient instance;

    private final WeatherApiService weatherApiService;

    private RetrofitClient() {
        // Logging interceptor (chỉ log ở debug)
        HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
        loggingInterceptor.setLevel(
                com.example.footballbooking.BuildConfig.DEBUG
                        ? HttpLoggingInterceptor.Level.BODY
                        : HttpLoggingInterceptor.Level.NONE
        );

        // OkHttpClient với timeout hợp lý
        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .addInterceptor(loggingInterceptor)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build();

        // Retrofit instance cho Weather API
        Retrofit weatherRetrofit = new Retrofit.Builder()
                .baseUrl(Constants.WEATHER_BASE_URL)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        weatherApiService = weatherRetrofit.create(WeatherApiService.class);
    }

    public static synchronized RetrofitClient getInstance() {
        if (instance == null) instance = new RetrofitClient();
        return instance;
    }

    public WeatherApiService getWeatherApiService() {
        return weatherApiService;
    }
}
