package com.example.footballbooking.ui.customer.ai;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.bumptech.glide.Glide;
import com.example.footballbooking.data.model.WeatherData;
import com.example.footballbooking.databinding.FragmentAiSuggestionBinding;
import com.example.footballbooking.ui.common.adapter.AiPitchAdapter;
import com.example.footballbooking.utils.AiScoringEngine;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.material.snackbar.Snackbar;

import java.util.Locale;

/**
 * AiSuggestionFragment — Màn hình "AI Gợi ý".
 *
 * FLOW:
 * 1. Xin quyền GPS
 * 2. AiViewModel load 3 nguồn data song song
 * 3. Hiển thị weather card + danh sách sân được xếp hạng
 */
public class AiSuggestionFragment extends Fragment
        implements AiPitchAdapter.OnAiPitchClickListener {

    private FragmentAiSuggestionBinding binding;
    private AiViewModel aiViewModel;
    private AiPitchAdapter aiAdapter;
    private FusedLocationProviderClient fusedLocationClient;

    private final ActivityResultLauncher<String> locationLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(),
                    isGranted -> loadAi(isGranted)
            );

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentAiSuggestionBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        aiViewModel = new ViewModelProvider(this).get(AiViewModel.class);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());

        setupRecyclerView();
        setupRefreshButton();
        observeViewModel();
        requestLocationAndLoad();
    }

    private void setupRecyclerView() {
        aiAdapter = new AiPitchAdapter(this);
        binding.rvAiSuggestions.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvAiSuggestions.setAdapter(aiAdapter);
        binding.rvAiSuggestions.setNestedScrollingEnabled(false);
    }

    private void setupRefreshButton() {
        binding.btnRefresh.setOnClickListener(v -> {
            aiViewModel.refresh();
        });
    }

    private void observeViewModel() {
        // Observe AI suggestions
        aiViewModel.getAiSuggestions().observe(getViewLifecycleOwner(), resource -> {
            if (resource == null) return;
            switch (resource.status) {
                case LOADING:
                    showLoading(true);
                    break;
                case SUCCESS:
                    showLoading(false);
                    if (resource.data == null || resource.data.isEmpty()) {
                        showEmpty("Không tìm thấy sân phù hợp 😢");
                    } else {
                        showList(resource.data);
                    }
                    break;
                case ERROR:
                    showLoading(false);
                    showEmpty("⚠️ " + resource.message);
                    Snackbar.make(binding.getRoot(), resource.message, Snackbar.LENGTH_LONG)
                            .setAction("Thử lại", v -> aiViewModel.refresh())
                            .show();
                    break;
            }
        });

        // Observe weather
        aiViewModel.getWeatherData().observe(getViewLifecycleOwner(), resource -> {
            if (resource != null && resource.isSuccess() && resource.data != null) {
                bindWeatherCard(resource.data);
            }
        });
    }

    private void requestLocationAndLoad() {
        if (ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            loadAi(true);
        } else {
            locationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
        }
    }

    private void loadAi(boolean hasPermission) {
        if (hasPermission && ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                    .addOnSuccessListener(location -> aiViewModel.loadAiSuggestions(location))
                    .addOnFailureListener(e -> aiViewModel.loadAiSuggestions(null));
        } else {
            aiViewModel.loadAiSuggestions(null); // Không có GPS → dùng Hà Nội fallback
        }
    }

    // ============================================================
    // BIND WEATHER CARD
    // ============================================================

    private void bindWeatherCard(WeatherData weather) {
        binding.cardWeather.setVisibility(View.VISIBLE);

        String mainText = weather.getWeatherEmoji() + " " + weather.getDescription();
        binding.tvWeatherMain.setText(
                Character.toUpperCase(mainText.charAt(0)) + mainText.substring(1));

        binding.tvWeatherDetail.setText(String.format(Locale.getDefault(),
                "%.0f°C · Độ ẩm %d%% · Gió %.1f m/s",
                weather.getTemperature(),
                weather.getHumidity(),
                weather.getWindSpeed()));

        // Weather score → lời khuyên
        float score = weather.getWeatherScore();
        if (score >= 0.8f) {
            binding.tvWeatherAdvice.setText("✅ Thời tiết tuyệt vời để đá bóng!");
            binding.tvWeatherAdvice.setTextColor(getResources().getColor(
                    com.example.footballbooking.R.color.status_approved, null));
        } else if (score >= 0.5f) {
            binding.tvWeatherAdvice.setText("⚠️ Thời tiết chấp nhận được, chuẩn bị thêm");
            binding.tvWeatherAdvice.setTextColor(getResources().getColor(
                    com.example.footballbooking.R.color.status_pending, null));
        } else {
            binding.tvWeatherAdvice.setText("❌ Thời tiết không thuận lợi, cân nhắc hoãn");
            binding.tvWeatherAdvice.setTextColor(getResources().getColor(
                    com.example.footballbooking.R.color.status_rejected, null));
        }

        binding.tvWeatherScore.setText(Math.round(score * 100) + "%");

        // Load icon
        if (weather.getWeather() != null && !weather.getWeather().isEmpty()) {
            Glide.with(this)
                    .load(weather.getWeather().get(0).getIconUrl())
                    .placeholder(android.R.drawable.ic_menu_report_image)
                    .error(android.R.drawable.ic_menu_report_image)
                    .into(binding.ivWeatherIcon);
        }
    }

    // ============================================================
    // UI STATE HELPERS
    // ============================================================

    private void showLoading(boolean loading) {
        if (loading) {
            binding.shimmerAi.startShimmer();
            binding.shimmerAi.setVisibility(View.VISIBLE);
            binding.rvAiSuggestions.setVisibility(View.GONE);
            binding.layoutAiEmpty.setVisibility(View.GONE);
        } else {
            binding.shimmerAi.stopShimmer();
            binding.shimmerAi.setVisibility(View.GONE);
        }
    }

    private void showList(java.util.List<AiScoringEngine.ScoredPitch> list) {
        binding.rvAiSuggestions.setVisibility(View.VISIBLE);
        binding.layoutAiEmpty.setVisibility(View.GONE);
        aiAdapter.submitList(list);
    }

    private void showEmpty(String message) {
        binding.rvAiSuggestions.setVisibility(View.GONE);
        binding.layoutAiEmpty.setVisibility(View.VISIBLE);
        binding.tvAiErrorMsg.setText(message);
    }

    // --- AiPitchAdapter Callback ---
    @Override
    public void onClick(AiScoringEngine.ScoredPitch scoredPitch) {
        // Navigate to PitchDetailActivity
        android.content.Intent intent = new android.content.Intent(
                requireActivity(),
                com.example.footballbooking.ui.customer.pitch.PitchDetailActivity.class);
        intent.putExtra(com.example.footballbooking.utils.Constants.EXTRA_PITCH_ID,
                scoredPitch.pitch.getPitchId());
        startActivity(intent);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
