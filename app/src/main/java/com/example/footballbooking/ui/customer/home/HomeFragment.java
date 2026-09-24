package com.example.footballbooking.ui.customer.home;

import android.content.Intent;
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
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
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.footballbooking.R;
import com.example.footballbooking.data.model.Pitch;
import com.example.footballbooking.databinding.FragmentHomeBinding;
import com.example.footballbooking.ui.common.adapter.PitchAdapter;
import com.example.footballbooking.ui.customer.pitch.PitchViewModel;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.material.snackbar.Snackbar;

/**
 * HomeFragment — Trang chủ của Customer.
 *
 * MVVM FLOW:
 * HomeFragment observe PitchViewModel (Activity scope) → update RecyclerView.
 * PitchViewModel được share với các fragment khác → không reload data khi switch tab.
 *
 * PATTERN: Fragment chỉ chứa UI logic, không có business logic.
 */
public class HomeFragment extends Fragment implements PitchAdapter.OnPitchClickListener {

    private FragmentHomeBinding binding;
    private HomeViewModel homeViewModel;
    private PitchViewModel pitchViewModel; // Shared ViewModel (Activity scope)
    private PitchAdapter pitchAdapter;
    private FusedLocationProviderClient fusedLocationClient;

    // Permission launcher (Android 6+) — xin quyền GPS
    private final ActivityResultLauncher<String> locationPermissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(),
                    isGranted -> {
                        if (isGranted) {
                            fetchUserLocation();
                        }
                        // Nếu từ chối thì vẫn hiển thị sân, chỉ không có khoảng cách
                    }
            );

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // ViewModel: HomeViewModel scope = Fragment, PitchViewModel scope = Activity
        homeViewModel  = new ViewModelProvider(this).get(HomeViewModel.class);
        pitchViewModel = new ViewModelProvider(requireActivity()).get(PitchViewModel.class);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());

        setupRecyclerView();
        setupFilterChips();
        setupSearch();
        setupClickListeners();
        observeViewModels();
        requestLocationPermission();
    }

    // ============================================================
    // SETUP
    // ============================================================

    private void setupRecyclerView() {
        pitchAdapter = new PitchAdapter(this);
        binding.rvPitches.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvPitches.setAdapter(pitchAdapter);
        // Tắt nested scroll để CoordinatorLayout xử lý scroll
        binding.rvPitches.setNestedScrollingEnabled(false);
    }

    private void setupFilterChips() {
        binding.chipGroupFilter.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int checkedId = checkedIds.get(0);

            if (checkedId == R.id.chip_all) {
                pitchViewModel.loadAllPitches();
            } else if (checkedId == R.id.chip_5) {
                pitchViewModel.filterByType("5");
            } else if (checkedId == R.id.chip_7) {
                pitchViewModel.filterByType("7");
            } else if (checkedId == R.id.chip_11) {
                pitchViewModel.filterByType("11");
            }
        });
    }

    private void setupSearch() {
        binding.etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int i, int i1, int i2) {}
            @Override public void onTextChanged(CharSequence s, int i, int i1, int i2) {}

            @Override
            public void afterTextChanged(Editable s) {
                // Debounce: chỉ search sau khi user ngừng gõ 500ms
                binding.etSearch.removeCallbacks(searchRunnable);
                binding.etSearch.postDelayed(searchRunnable, 500);
            }

            private final Runnable searchRunnable = () -> {
                String query = binding.etSearch.getText() != null
                        ? binding.etSearch.getText().toString() : "";
                pitchViewModel.searchPitches(query);
            };
        });
    }

    private void setupClickListeners() {
        // Xem tất cả → chuyển sang tab Bản đồ
        binding.tvViewAll.setOnClickListener(v -> navigateToTab(R.id.nav_map));

        // AI banner → sang tab AI
        binding.btnViewAi.setOnClickListener(v -> navigateToTab(R.id.nav_ai));

        // FAB đặt sân nhanh
        binding.fabQuickBook.setOnClickListener(v ->
                Snackbar.make(v, "Chọn sân để đặt!", Snackbar.LENGTH_SHORT).show()
        );
    }

    private void navigateToTab(int tabId) {
        if (getActivity() instanceof com.example.footballbooking.ui.customer.CustomerMainActivity) {
            ((com.example.footballbooking.ui.customer.CustomerMainActivity) getActivity()).selectBottomNavTab(tabId);
        } else {
            Navigation.findNavController(requireView()).navigate(tabId);
        }
    }

    // ============================================================
    // OBSERVE
    // ============================================================

    private void observeViewModels() {
        // Observe thông tin user (greeting + avatar)
        homeViewModel.getCurrentUser().observe(getViewLifecycleOwner(), resource -> {
            if (resource != null && resource.isSuccess() && resource.data != null) {
                binding.tvGreeting.setText(homeViewModel.getGreeting());
                binding.tvUserName.setText(resource.data.getDisplayName());

                // Load avatar với Glide (nếu có)
                if (resource.data.getAvatarUrl() != null
                        && !resource.data.getAvatarUrl().isEmpty()) {
                    com.bumptech.glide.Glide.with(this)
                            .load(resource.data.getAvatarUrl())
                            .placeholder(android.R.drawable.ic_menu_myplaces)
                            .error(android.R.drawable.ic_menu_myplaces)
                            .circleCrop()
                            .into(binding.civAvatar);
                }
            }
        });

        // Observe danh sách sân
        pitchViewModel.getPitchList().observe(getViewLifecycleOwner(), resource -> {
            if (resource == null) return;

            switch (resource.status) {
                case LOADING:
                    showShimmer(true);
                    break;

                case SUCCESS:
                    showShimmer(false);
                    if (resource.data == null || resource.data.isEmpty()) {
                        showEmptyState(true);
                    } else {
                        showEmptyState(false);
                        pitchAdapter.submitList(resource.data); // DiffUtil xử lý tự động
                    }
                    break;

                case ERROR:
                    showShimmer(false);
                    Snackbar.make(binding.getRoot(),
                            "Lỗi: " + resource.message,
                            Snackbar.LENGTH_LONG)
                            .setAction("Thử lại", v -> pitchViewModel.loadAllPitches())
                            .show();
                    break;
            }
        });
    }

    // ============================================================
    // GPS / LOCATION
    // ============================================================

    private void requestLocationPermission() {
        if (ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fetchUserLocation();
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
        }
    }

    private void fetchUserLocation() {
        if (ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                .addOnSuccessListener(location -> {
                    if (location != null) {
                        // Truyền location cho Adapter để hiển thị khoảng cách
                        pitchAdapter.setUserLocation(location);
                    }
                });
    }

    // ============================================================
    // UI HELPERS
    // ============================================================

    private void showShimmer(boolean show) {
        if (show) {
            binding.shimmerPitches.startShimmer();
            binding.shimmerPitches.setVisibility(View.VISIBLE);
            binding.rvPitches.setVisibility(View.GONE);
        } else {
            binding.shimmerPitches.stopShimmer();
            binding.shimmerPitches.setVisibility(View.GONE);
            binding.rvPitches.setVisibility(View.VISIBLE);
        }
    }

    private void showEmptyState(boolean show) {
        binding.layoutEmpty.setVisibility(show ? View.VISIBLE : View.GONE);
        binding.rvPitches.setVisibility(show ? View.GONE : View.VISIBLE);
    }

    // ============================================================
    // CLICK CALLBACK từ PitchAdapter
    // ============================================================

    @Override
    public void onPitchClick(Pitch pitch) {
        android.widget.Toast.makeText(requireContext(), "Đang mở: " + pitch.getName(), android.widget.Toast.LENGTH_SHORT).show();
        // Navigate sang PitchDetailActivity với pitchId
        Intent intent = new Intent(requireActivity(),
                com.example.footballbooking.ui.customer.pitch.PitchDetailActivity.class);
        intent.putExtra(com.example.footballbooking.utils.Constants.EXTRA_PITCH_ID,
                pitch.getPitchId());
        try {
            startActivity(intent);
        } catch (Exception e) {
            android.widget.Toast.makeText(requireContext(), "Lỗi khởi chạy: " + e.getMessage(), android.widget.Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null; // Tránh memory leak — ViewBinding giữ reference đến View
    }
}
