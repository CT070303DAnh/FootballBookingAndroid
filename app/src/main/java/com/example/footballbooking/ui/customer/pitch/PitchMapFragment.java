package com.example.footballbooking.ui.customer.pitch;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
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

import com.bumptech.glide.Glide;
import com.example.footballbooking.R;
import com.example.footballbooking.data.model.Pitch;
import com.example.footballbooking.databinding.FragmentPitchMapBinding;
import com.example.footballbooking.utils.Constants;
import com.example.footballbooking.utils.Resource;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.snackbar.Snackbar;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * PitchMapFragment — Bản đồ tìm sân bóng với Google Maps SDK.
 * Hiển thị vị trí người dùng, các sân bóng theo vị trí GPS,
 * lọc theo loại sân (5, 7, 11) và xem trước thông tin sân bóng.
 */
public class PitchMapFragment extends Fragment implements OnMapReadyCallback {

    private FragmentPitchMapBinding binding;
    private GoogleMap googleMap;
    private PitchViewModel pitchViewModel;
    private FusedLocationProviderClient fusedLocationClient;

    private final List<Pitch> currentPitchList = new ArrayList<>();
    private final Map<Marker, Pitch> markerPitchMap = new HashMap<>();
    private Pitch selectedPitch;

    private final NumberFormat currencyFmt =
            NumberFormat.getNumberInstance(new Locale("vi", "VN"));

    private final ActivityResultLauncher<String[]> locationPermissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestMultiplePermissions(),
                    result -> {
                        Boolean fineLocationGranted = result.getOrDefault(
                                Manifest.permission.ACCESS_FINE_LOCATION, false);
                        Boolean coarseLocationGranted = result.getOrDefault(
                                Manifest.permission.ACCESS_COARSE_LOCATION, false);
                        if (fineLocationGranted != null && fineLocationGranted
                                || coarseLocationGranted != null && coarseLocationGranted) {
                            enableMyLocation();
                        }
                    }
            );

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentPitchMapBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        pitchViewModel = new ViewModelProvider(requireActivity()).get(PitchViewModel.class);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());

        setupMap();
        setupFilterChips();
        setupClickListeners();
        observeViewModel();
    }

    private void setupMap() {
        SupportMapFragment mapFragment = (SupportMapFragment)
                getChildFragmentManager().findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
    }

    @Override
    public void onMapReady(@NonNull GoogleMap map) {
        this.googleMap = map;

        // Cấu hình giao diện bản đồ
        googleMap.getUiSettings().setZoomControlsEnabled(false);
        googleMap.getUiSettings().setMyLocationButtonEnabled(false);
        googleMap.getUiSettings().setCompassEnabled(true);

        // Click marker → mở card preview
        googleMap.setOnMarkerClickListener(marker -> {
            Pitch pitch = markerPitchMap.get(marker);
            if (pitch != null) {
                showPitchPreview(pitch);
                googleMap.animateCamera(CameraUpdateFactory.newLatLng(marker.getPosition()), 300, null);
            }
            return true;
        });

        // Click ngoài map → ẩn card preview
        googleMap.setOnMapClickListener(latLng -> hidePitchPreview());

        // Kiểm tra quyền vị trí
        checkLocationPermissionAndCenter();

        // Vẽ lại markers nếu data đã load trước khi map ready
        plotPitchesOnMap();
    }

    private void checkLocationPermissionAndCenter() {
        if (hasLocationPermission()) {
            enableMyLocation();
        } else {
            // Tọa độ mặc định (Việt Nam) nếu chưa có quyền
            LatLng defaultLocation = new LatLng(21.0285, 105.8542); // Hà Nội
            if (googleMap != null) {
                googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, 12f));
            }
            locationPermissionLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            });
        }
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void enableMyLocation() {
        if (googleMap == null || !hasLocationPermission()) return;

        try {
            googleMap.setMyLocationEnabled(true);
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                    .addOnSuccessListener(location -> {
                        if (location != null) {
                            LatLng myLatLng = new LatLng(location.getLatitude(), location.getLongitude());
                            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(myLatLng, 14f));
                        }
                    });
        } catch (SecurityException ignored) {
        }
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

    private void setupClickListeners() {
        // Nút vị trí hiện tại
        binding.fabMyLocation.setOnClickListener(v -> {
            if (hasLocationPermission()) {
                enableMyLocation();
            } else {
                locationPermissionLauncher.launch(new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                });
            }
        });

        // Nút refresh map
        binding.fabRefreshMap.setOnClickListener(v -> {
            pitchViewModel.loadAllPitches();
            binding.chipAll.setChecked(true);
        });

        // Nút đóng card preview
        binding.btnClosePreview.setOnClickListener(v -> hidePitchPreview());

        // Nút xem chi tiết & đặt sân
        binding.btnPreviewDetail.setOnClickListener(v -> {
            if (selectedPitch != null) {
                Intent intent = new Intent(requireActivity(), PitchDetailActivity.class);
                intent.putExtra(Constants.EXTRA_PITCH_ID, selectedPitch.getPitchId());
                startActivity(intent);
            }
        });
    }

    private void observeViewModel() {
        pitchViewModel.getPitchList().observe(getViewLifecycleOwner(), resource -> {
            if (resource == null) return;
            switch (resource.status) {
                case LOADING:
                    binding.progressMap.setVisibility(View.VISIBLE);
                    break;
                case SUCCESS:
                    binding.progressMap.setVisibility(View.GONE);
                    currentPitchList.clear();
                    if (resource.data != null) {
                        currentPitchList.addAll(resource.data);
                    }
                    plotPitchesOnMap();
                    break;
                case ERROR:
                    binding.progressMap.setVisibility(View.GONE);
                    Snackbar.make(binding.getRoot(),
                            "Lỗi tải sân: " + resource.message, Snackbar.LENGTH_LONG).show();
                    break;
            }
        });
    }

    private void plotPitchesOnMap() {
        if (googleMap == null) return;

        googleMap.clear();
        markerPitchMap.clear();

        for (Pitch pitch : currentPitchList) {
            if (pitch.getLocation() != null) {
                LatLng pos = new LatLng(
                        pitch.getLocation().getLatitude(),
                        pitch.getLocation().getLongitude()
                );

                MarkerOptions options = new MarkerOptions()
                        .position(pos)
                        .title(pitch.getName())
                        .snippet(pitch.getAddress())
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN));

                Marker marker = googleMap.addMarker(options);
                if (marker != null) {
                    markerPitchMap.put(marker, pitch);
                }
            }
        }
    }

    private void showPitchPreview(Pitch pitch) {
        this.selectedPitch = pitch;

        binding.tvPreviewName.setText(pitch.getName());
        binding.tvPreviewAddress.setText(pitch.getAddress() != null ? pitch.getAddress() : "Chưa có địa chỉ");
        binding.tvPreviewType.setText(pitch.getDisplayType());
        binding.tvPreviewRating.setText(String.format(Locale.getDefault(), "⭐ %.1f", pitch.getRating()));
        binding.tvPreviewPrice.setText(String.format("%s đ/giờ", currencyFmt.format(pitch.getBasePrice())));

        // Load hình ảnh sân
        if (pitch.getImageUrls() != null && !pitch.getImageUrls().isEmpty()) {
            Glide.with(this)
                    .load(pitch.getImageUrls().get(0))
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .error(android.R.drawable.ic_menu_gallery)
                    .into(binding.ivPreviewImage);
        } else {
            binding.ivPreviewImage.setImageResource(android.R.drawable.ic_menu_gallery);
        }

        binding.cardPitchPreview.setVisibility(View.VISIBLE);
    }

    private void hidePitchPreview() {
        binding.cardPitchPreview.setVisibility(View.GONE);
        selectedPitch = null;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
