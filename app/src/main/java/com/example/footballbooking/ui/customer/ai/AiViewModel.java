package com.example.footballbooking.ui.customer.ai;

import android.location.Location;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.footballbooking.data.model.Booking;
import com.example.footballbooking.data.model.Pitch;
import com.example.footballbooking.data.model.WeatherData;
import com.example.footballbooking.data.repository.BookingRepository;
import com.example.footballbooking.data.repository.PitchRepository;
import com.example.footballbooking.data.repository.WeatherRepository;
import com.example.footballbooking.utils.AiScoringEngine;
import com.example.footballbooking.utils.Resource;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AiViewModel — Orchestrate AI gợi ý sân.
 *
 * FLOW:
 * 1. Load tất cả sân từ Firestore
 * 2. Lấy thời tiết hiện tại từ OpenWeatherMap
 * 3. Lấy lịch sử đặt sân của user
 * 4. Khi có đủ dữ liệu → chạy AiScoringEngine → emit kết quả
 *
 * PATTERN: Chờ cả 3 data source xong mới tính điểm (fan-in pattern).
 */
public class AiViewModel extends ViewModel {

    private final PitchRepository   pitchRepository;
    private final WeatherRepository weatherRepository;
    private final BookingRepository bookingRepository;

    // --- Raw data từ các nguồn ---
    private final MutableLiveData<Resource<List<Pitch>>>   allPitches  = new MutableLiveData<>();
    private final MutableLiveData<Resource<WeatherData>>   weatherData = new MutableLiveData<>();
    private final MutableLiveData<Resource<List<Booking>>> userHistory = new MutableLiveData<>();

    // --- Kết quả AI (Fragment observe cái này) ---
    private final MutableLiveData<Resource<List<AiScoringEngine.ScoredPitch>>>
            aiSuggestions = new MutableLiveData<>();

    // Cache trạng thái dữ liệu đã nhận
    private List<Pitch>   cachedPitches;
    private WeatherData   cachedWeather;
    private Map<String, Integer> cachedHistoryMap; // pitchId → count

    private Location userLocation;

    public AiViewModel() {
        pitchRepository   = PitchRepository.getInstance();
        weatherRepository = WeatherRepository.getInstance();
        bookingRepository = BookingRepository.getInstance();

        observeRawData();
    }

    // ============================================================
    // EXPOSE LiveData
    // ============================================================

    public MutableLiveData<Resource<List<AiScoringEngine.ScoredPitch>>> getAiSuggestions() {
        return aiSuggestions;
    }

    public MutableLiveData<Resource<WeatherData>> getWeatherData() {
        return weatherData;
    }

    // ============================================================
    // ACTIONS
    // ============================================================

    /**
     * Khởi động quá trình gợi ý AI.
     * Gọi từ Fragment sau khi có GPS permission.
     */
    public void loadAiSuggestions(Location location) {
        this.userLocation = location;
        aiSuggestions.setValue(Resource.loading(null));

        // Load song song 3 nguồn dữ liệu
        loadAllPitches();
        loadWeather(location);
        loadUserHistory();
    }

    /** Force refresh (pull-to-refresh) */
    public void refresh() {
        weatherRepository.clearCache();
        loadAiSuggestions(userLocation);
    }

    // ============================================================
    // PRIVATE: Load data sources
    // ============================================================

    private void loadAllPitches() {
        pitchRepository.getAllPitches(allPitches);
    }

    private void loadWeather(Location location) {
        if (location != null) {
            weatherRepository.getWeatherByCoords(
                    location.getLatitude(), location.getLongitude(), weatherData);
        } else {
            weatherRepository.getWeatherHanoi(weatherData); // Fallback
        }
    }

    private void loadUserHistory() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            cachedHistoryMap = new HashMap<>();
            tryCalculateScore();
            return;
        }
        bookingRepository.getBookingsByCustomer(user.getUid(), userHistory);
    }

    // ============================================================
    // PRIVATE: Fan-in — Observe tất cả sources, tính điểm khi đủ data
    // ============================================================

    private void observeRawData() {

        // Observe pitches
        allPitches.observeForever(resource -> {
            if (resource != null && resource.isSuccess()) {
                cachedPitches = resource.data;
                tryCalculateScore();
            } else if (resource != null && resource.isError()) {
                aiSuggestions.postValue(Resource.error("Lỗi tải sân: " + resource.message, null));
            }
        });

        // Observe weather
        weatherData.observeForever(resource -> {
            if (resource != null && resource.isSuccess()) {
                cachedWeather = resource.data;
                tryCalculateScore();
            } else if (resource != null && resource.isError()) {
                // Weather lỗi → vẫn tính với weather = null (score = 0.5)
                cachedWeather = null;
                tryCalculateScore();
            }
        });

        // Observe booking history
        userHistory.observeForever(resource -> {
            if (resource != null && resource.isSuccess()) {
                cachedHistoryMap = buildHistoryMap(resource.data);
                tryCalculateScore();
            } else if (resource != null && resource.isError()) {
                cachedHistoryMap = new HashMap<>();
                tryCalculateScore();
            }
        });
    }

    /**
     * Chỉ tính điểm khi đã có đủ: pitches + weather flag + history flag.
     * Weather và history có thể null (fallback).
     */
    private void tryCalculateScore() {
        // Chờ tất cả data sẵn sàng (pitches bắt buộc, rest optional)
        if (cachedPitches == null) return;
        if (cachedHistoryMap == null) return;
        // cachedWeather có thể null → OK

        // Chạy AI scoring trên background thread
        new Thread(() -> {
            List<AiScoringEngine.ScoredPitch> scored = AiScoringEngine.rankPitches(
                    cachedPitches,
                    userLocation,
                    cachedWeather,
                    cachedHistoryMap
            );
            // Chỉ lấy top 10 gợi ý
            List<AiScoringEngine.ScoredPitch> top10 =
                    scored.size() > 10 ? scored.subList(0, 10) : scored;

            aiSuggestions.postValue(Resource.success(new ArrayList<>(top10)));
        }).start();
    }

    /** Chuyển List<Booking> → Map<pitchId, bookingCount> */
    private Map<String, Integer> buildHistoryMap(List<Booking> bookings) {
        Map<String, Integer> map = new HashMap<>();
        if (bookings == null) return map;
        for (Booking b : bookings) {
            if (b.getPitchId() != null) {
                map.put(b.getPitchId(), map.getOrDefault(b.getPitchId(), 0) + 1);
            }
        }
        return map;
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        pitchRepository.detachPitchListListener();
        bookingRepository.detachBookingListListener();
    }
}
