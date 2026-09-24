package com.example.footballbooking.ui.owner.pitch;

import android.Manifest;
import android.app.Dialog;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;
import com.example.footballbooking.R;
import com.example.footballbooking.data.model.Pitch;
import com.example.footballbooking.databinding.DialogAddEditPitchBinding;
import com.example.footballbooking.ui.owner.OwnerViewModel;
import com.example.footballbooking.utils.Constants;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.firestore.GeoPoint;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PitchAddEditDialogFragment extends BottomSheetDialogFragment implements OnMapReadyCallback {

    private DialogAddEditPitchBinding binding;
    private OwnerViewModel ownerViewModel;
    private FusedLocationProviderClient fusedLocationClient;

    private Pitch editingPitch;

    private GoogleMap googleMap;
    private Marker locationMarker;
    private LatLng selectedLatLng = new LatLng(21.028511, 105.782302); // Mặc định Hà Nội

    private static final String DEFAULT_PITCH_IMAGE = "https://images.unsplash.com/photo-1574629810360-7efbbe195018?q=80&w=800";

    private final ActivityResultLauncher<String> locationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    moveToCurrentGpsLocation();
                } else {
                    Snackbar.make(binding.getRoot(), "Bạn từ chối quyền vị trí. Vui lòng tự tìm địa chỉ.", Snackbar.LENGTH_SHORT).show();
                }
            });

    public static PitchAddEditDialogFragment newInstance(@Nullable String pitchId) {
        PitchAddEditDialogFragment fragment = new PitchAddEditDialogFragment();
        Bundle args = new Bundle();
        if (pitchId != null) {
            args.putString("PITCH_ID", pitchId);
        }
        fragment.setArguments(args);
        return fragment;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        dialog.setOnShowListener(dialogInterface -> {
            BottomSheetDialog bottomSheetDialog = (BottomSheetDialog) dialogInterface;
            View bottomSheet = bottomSheetDialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bottomSheet);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                behavior.setSkipCollapsed(true);
            }
        });
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = DialogAddEditPitchBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ownerViewModel = new ViewModelProvider(requireActivity()).get(OwnerViewModel.class);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());

        if (getArguments() != null && getArguments().containsKey("PITCH_ID")) {
            String pitchId = getArguments().getString("PITCH_ID");
            if (ownerViewModel.getMyPitches().getValue() != null && ownerViewModel.getMyPitches().getValue().data != null) {
                for (Pitch p : ownerViewModel.getMyPitches().getValue().data) {
                    if (p.getPitchId().equals(pitchId)) {
                        editingPitch = p;
                        break;
                    }
                }
            }
        }

        setupDropdowns();
        setupFields();
        setupMap();
        setupListeners();

        ownerViewModel.getPitchCreateResult().observe(getViewLifecycleOwner(), resource -> {
            if (resource == null) return;
            if (resource.isSuccess() && resource.data != null) {
                dismiss();
            }
        });
    }

    private void setupDropdowns() {
        String[] types = {"5", "7", "11"};
        binding.actPitchType.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_dropdown_item_1line, types));

        String[] statuses = {Constants.PITCH_AVAILABLE, Constants.PITCH_MAINTENANCE, Constants.PITCH_CLOSED};
        binding.actPitchStatus.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_dropdown_item_1line, statuses));
    }

    private void setupFields() {
        if (editingPitch != null) {
            binding.etPitchName.setText(editingPitch.getName());
            binding.etPitchAddress.setText(editingPitch.getAddress());
            binding.etPitchPrice.setText(String.valueOf((long) editingPitch.getBasePrice()));
            binding.etPitchDescription.setText(editingPitch.getDescription());
            binding.actPitchType.setText(editingPitch.getType(), false);
            binding.actPitchStatus.setText(editingPitch.getStatus(), false);

            if (editingPitch.getLocation() != null) {
                selectedLatLng = new LatLng(editingPitch.getLocation().getLatitude(), editingPitch.getLocation().getLongitude());
            }

            setAmenityChips(editingPitch.getAmenities());
        } else {
            binding.etPitchPrice.setText("200000");
        }
    }

    private void setupMap() {
        SupportMapFragment mapFragment = (SupportMapFragment) getChildFragmentManager().findFragmentById(R.id.map_pitch_location);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
    }

    private void setupListeners() {
        binding.btnGetLocation.setOnClickListener(v -> checkPermissionAndGetLocation());
        binding.btnCancel.setOnClickListener(v -> dismiss());
        binding.btnSave.setOnClickListener(v -> savePitch());

        binding.btnSearchAddress.setOnClickListener(v -> {
            String address = binding.etPitchAddress.getText() != null ? binding.etPitchAddress.getText().toString().trim() : "";
            if (!address.isEmpty()) {
                searchAddressOnMap(address);
            } else {
                Snackbar.make(binding.getRoot(), "Vui lòng nhập địa chỉ trước khi tìm", Snackbar.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onMapReady(@NonNull GoogleMap map) {
        this.googleMap = map;
        this.googleMap.getUiSettings().setZoomControlsEnabled(true);
        this.googleMap.getUiSettings().setScrollGesturesEnabled(true);

        MarkerOptions markerOptions = new MarkerOptions()
                .position(selectedLatLng)
                .title("Vị trí Sân")
                .draggable(true);
        locationMarker = googleMap.addMarker(markerOptions);
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(selectedLatLng, 15f));

        googleMap.setOnMarkerDragListener(new GoogleMap.OnMarkerDragListener() {
            @Override
            public void onMarkerDragStart(@NonNull Marker marker) {}

            @Override
            public void onMarkerDrag(@NonNull Marker marker) {}

            @Override
            public void onMarkerDragEnd(@NonNull Marker marker) {
                selectedLatLng = marker.getPosition();
                Snackbar.make(binding.getRoot(), "📍 Đã cập nhật tọa độ", Snackbar.LENGTH_SHORT).show();
            }
        });

        googleMap.setOnMapClickListener(latLng -> {
            selectedLatLng = latLng;
            if (locationMarker != null) {
                locationMarker.setPosition(latLng);
            }
        });
    }

    private void searchAddressOnMap(String addressText) {
        if (getContext() == null || googleMap == null) return;
        Geocoder geocoder = new Geocoder(getContext());
        new Thread(() -> {
            try {
                List<Address> addresses = geocoder.getFromLocationName(addressText, 1);
                if (addresses != null && !addresses.isEmpty()) {
                    Address address = addresses.get(0);
                    LatLng foundLatLng = new LatLng(address.getLatitude(), address.getLongitude());
                    requireActivity().runOnUiThread(() -> {
                        selectedLatLng = foundLatLng;
                        if (locationMarker != null) {
                            locationMarker.setPosition(foundLatLng);
                        }
                        googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(foundLatLng, 15f));
                    });
                } else {
                    requireActivity().runOnUiThread(() -> 
                        Snackbar.make(binding.getRoot(), "Không tìm thấy địa chỉ này trên bản đồ", Snackbar.LENGTH_SHORT).show()
                    );
                }
            } catch (IOException e) {
                requireActivity().runOnUiThread(() -> 
                    Snackbar.make(binding.getRoot(), "Lỗi mạng khi tìm địa chỉ", Snackbar.LENGTH_SHORT).show()
                );
            }
        }).start();
    }

    private void checkPermissionAndGetLocation() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            moveToCurrentGpsLocation();
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
        }
    }

    private void moveToCurrentGpsLocation() {
        try {
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                    .addOnSuccessListener(location -> {
                        if (location != null && googleMap != null) {
                            selectedLatLng = new LatLng(location.getLatitude(), location.getLongitude());
                            if (locationMarker != null) {
                                locationMarker.setPosition(selectedLatLng);
                            }
                            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(selectedLatLng, 16f));
                            Snackbar.make(binding.getRoot(), "📍 Đã di chuyển đến vị trí hiện tại", Snackbar.LENGTH_SHORT).show();
                        }
                    });
        } catch (SecurityException ignored) {
        }
    }

    private void savePitch() {
        String name = getText(binding.etPitchName);
        String address = getText(binding.etPitchAddress);
        String priceStr = getText(binding.etPitchPrice);
        String type = binding.actPitchType.getText().toString();
        String status = binding.actPitchStatus.getText().toString();
        String description = getText(binding.etPitchDescription);

        if (name.isEmpty() || address.isEmpty() || priceStr.isEmpty()) {
            Snackbar.make(binding.getRoot(), "Vui lòng nhập tên sân, địa chỉ và giá thuê!", Snackbar.LENGTH_SHORT).show();
            return;
        }

        double price;
        try {
            price = Double.parseDouble(priceStr);
        } catch (NumberFormatException e) {
            Snackbar.make(binding.getRoot(), "Giá thuê không hợp lệ", Snackbar.LENGTH_SHORT).show();
            return;
        }

        Pitch pitch = editingPitch != null ? editingPitch : new Pitch();
        pitch.setName(name);
        pitch.setAddress(address);
        pitch.setBasePrice(price);
        pitch.setType(type.isEmpty() ? "5" : type);
        pitch.setStatus(status.isEmpty() ? Constants.PITCH_AVAILABLE : status);
        pitch.setDescription(description);
        pitch.setLocation(new GeoPoint(selectedLatLng.latitude, selectedLatLng.longitude));
        pitch.setAmenities(getSelectedAmenities());

        if (pitch.getImageUrls() == null || pitch.getImageUrls().isEmpty()) {
            pitch.setImageUrls(new ArrayList<>(Collections.singletonList(DEFAULT_PITCH_IMAGE)));
        }

        if (editingPitch == null) {
            ownerViewModel.createPitch(pitch);
            // Will dismiss in observer
        } else {
            ownerViewModel.updatePitch(pitch);
            dismiss();
        }
    }

    private void setAmenityChips(List<String> amenities) {
        if (amenities == null) return;
        if (amenities.contains("🅿️ Bãi đỗ xe")) binding.chipParking.setChecked(true);
        if (amenities.contains("🚿 Phòng tắm")) binding.chipShower.setChecked(true);
        if (amenities.contains("💡 Đèn đêm")) binding.chipLight.setChecked(true);
        if (amenities.contains("🏠 Mái che")) binding.chipCover.setChecked(true);
        if (amenities.contains("💧 Nước uống")) binding.chipWater.setChecked(true);
    }

    private List<String> getSelectedAmenities() {
        List<String> list = new ArrayList<>();
        if (binding.chipParking.isChecked()) list.add("🅿️ Bãi đỗ xe");
        if (binding.chipShower.isChecked()) list.add("🚿 Phòng tắm");
        if (binding.chipLight.isChecked()) list.add("💡 Đèn đêm");
        if (binding.chipCover.isChecked()) list.add("🏠 Mái che");
        if (binding.chipWater.isChecked()) list.add("💧 Nước uống");
        return list;
    }

    private String getText(com.google.android.material.textfield.TextInputEditText et) {
        return et.getText() != null ? et.getText().toString().trim() : "";
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
