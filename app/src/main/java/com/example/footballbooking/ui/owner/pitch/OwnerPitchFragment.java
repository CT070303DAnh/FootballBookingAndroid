package com.example.footballbooking.ui.owner.pitch;

import android.Manifest;
import android.content.pm.PackageManager;
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
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.bumptech.glide.Glide;
import com.example.footballbooking.data.model.Pitch;
import com.example.footballbooking.data.model.User;
import com.example.footballbooking.databinding.DialogAddEditPitchBinding;
import com.example.footballbooking.databinding.FragmentOwnerPitchBinding;
import com.example.footballbooking.ui.common.adapter.AdminPitchAdapter;
import com.example.footballbooking.ui.owner.OwnerViewModel;
import com.example.footballbooking.utils.Constants;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.firebase.firestore.GeoPoint;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * OwnerPitchFragment — Quản lý và Thêm sân bóng của chủ sân.
 * Hỗ trợ nhập Sân số/Tên sân, Địa chỉ, Tọa độ GPS (để hiển thị trên Bản đồ người dùng),
 * Loại sân, Giá thuê, và Ảnh sân.
 */
public class OwnerPitchFragment extends Fragment
        implements AdminPitchAdapter.OnAdminPitchActionListener {

    private FragmentOwnerPitchBinding binding;
    private OwnerViewModel ownerViewModel;
    private AdminPitchAdapter pitchAdapter;

    private DialogAddEditPitchBinding dialogBinding;
    private Uri selectedImageUri;
    private Pitch editingPitch;
    private FusedLocationProviderClient fusedLocationClient;

    // Ảnh sân bóng mặc định chất lượng cao từ Unsplash nếu chủ sân chưa có ảnh
    private static final String DEFAULT_PITCH_IMAGE =
            "https://images.unsplash.com/photo-1529900248674-20b72322c342?q=80&w=800";

    private final ActivityResultLauncher<String> imagePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    selectedImageUri = uri;
                    if (dialogBinding != null) {
                        dialogBinding.tvPickImageHint.setVisibility(View.GONE);
                        dialogBinding.ivPitchPreview.setVisibility(View.VISIBLE);
                        Glide.with(this).load(uri).centerCrop().into(dialogBinding.ivPitchPreview);
                    }
                }
            });

    private final ActivityResultLauncher<String> locationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    fillCurrentGpsLocation();
                } else {
                    Snackbar.make(binding.getRoot(), "Chưa cấp quyền vị trí, bạn có thể tự nhập tọa độ", Snackbar.LENGTH_SHORT).show();
                }
            });

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentOwnerPitchBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ownerViewModel = new ViewModelProvider(requireActivity()).get(OwnerViewModel.class);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());

        setupHeader();
        setupRecyclerView();
        setupFab();
        observeViewModel();
    }

    private void setupHeader() {
        User owner = ownerViewModel.getCurrentOwner();
        if (owner == null) return;

        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String greeting = hour < 12 ? "Chào buổi sáng 🌤️"
                : hour < 18 ? "Chào buổi chiều ☀️"
                : "Chào buổi tối 🌙";
        binding.tvOwnerGreeting.setText(greeting);
        binding.tvOwnerName.setText(owner.getDisplayName());
    }

    private void setupRecyclerView() {
        pitchAdapter = new AdminPitchAdapter(this);
        binding.rvOwnerPitches.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvOwnerPitches.setAdapter(pitchAdapter);
    }

    private void setupFab() {
        binding.fabAddMyPitch.setOnClickListener(v -> openAddEditDialog(null));
        binding.btnHeaderAddPitch.setOnClickListener(v -> openAddEditDialog(null));
        binding.btnEmptyAddPitch.setOnClickListener(v -> openAddEditDialog(null));
    }

    private void observeViewModel() {
        ownerViewModel.getMyPitches().observe(getViewLifecycleOwner(), resource -> {
            if (resource == null) return;
            binding.progressOwnerPitch.setVisibility(
                    resource.isLoading() ? View.VISIBLE : View.GONE);
            if (resource.isSuccess() && resource.data != null) {
                if (resource.data.isEmpty()) {
                    binding.layoutOwnerEmptyPitch.setVisibility(View.VISIBLE);
                    binding.rvOwnerPitches.setVisibility(View.GONE);
                } else {
                    binding.layoutOwnerEmptyPitch.setVisibility(View.GONE);
                    binding.rvOwnerPitches.setVisibility(View.VISIBLE);
                    pitchAdapter.submitList(resource.data);
                }
                updateKpiStats(resource.data);
            } else if (resource.isError()) {
                Snackbar.make(binding.getRoot(), resource.message, Snackbar.LENGTH_LONG).show();
            }
        });

        ownerViewModel.getMyBookings().observe(getViewLifecycleOwner(), resource -> {
            if (resource != null && resource.isSuccess() && resource.data != null) {
                long pending = resource.data.stream()
                        .filter(b -> Constants.STATUS_PENDING.equals(b.getBookingStatus()))
                        .count();
                binding.tvStatPending.setText(String.valueOf(pending));
            }
        });

        ownerViewModel.getActionResult().observe(getViewLifecycleOwner(), resource -> {
            if (resource == null) return;
            if (resource.isSuccess())
                Snackbar.make(binding.getRoot(), "✅ Đã cập nhật thành công", Snackbar.LENGTH_SHORT).show();
            else if (resource.isError())
                Snackbar.make(binding.getRoot(), "Lỗi: " + resource.message, Snackbar.LENGTH_LONG).show();
        });

        ownerViewModel.getPitchCreateResult().observe(getViewLifecycleOwner(), resource -> {
            if (resource == null) return;
            if (resource.isSuccess() && resource.data != null) {
                Snackbar.make(binding.getRoot(), "🎉 Đã thêm sân \"" + resource.data.getName() + "\" lên hệ thống!", Snackbar.LENGTH_LONG).show();
                if (selectedImageUri != null) {
                    ownerViewModel.uploadPitchImage(resource.data.getPitchId(), selectedImageUri);
                }
            } else if (resource.isError()) {
                Snackbar.make(binding.getRoot(), "Lỗi thêm sân: " + resource.message, Snackbar.LENGTH_LONG).show();
            }
        });
    }

    private void updateKpiStats(List<Pitch> pitches) {
        binding.tvStatPitches.setText(String.valueOf(pitches.size()));
    }

    // ============================================================
    // DIALOG THÊM / CHỈNH SỬA SÂN BÓNG
    // ============================================================

    private void openAddEditDialog(@Nullable Pitch pitch) {
        editingPitch = pitch;
        selectedImageUri = null;
        dialogBinding = DialogAddEditPitchBinding.inflate(LayoutInflater.from(requireContext()));

        // Danh sách Loại sân
        String[] types = {"5", "7", "11"};
        dialogBinding.actPitchType.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_dropdown_item_1line, types));

        // Trạng thái sân
        String[] statuses = {Constants.PITCH_AVAILABLE, Constants.PITCH_MAINTENANCE, Constants.PITCH_CLOSED};
        dialogBinding.actPitchStatus.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_dropdown_item_1line, statuses));

        // Lắng nghe nút lấy GPS hiện tại
        dialogBinding.btnGetLocation.setOnClickListener(v -> checkPermissionAndGetLocation());

        if (pitch != null) {
            dialogBinding.etPitchName.setText(pitch.getName());
            dialogBinding.etPitchAddress.setText(pitch.getAddress());
            dialogBinding.etPitchPrice.setText(String.valueOf((long) pitch.getBasePrice()));
            dialogBinding.etPitchDescription.setText(pitch.getDescription());
            dialogBinding.actPitchType.setText(pitch.getType(), false);
            dialogBinding.actPitchStatus.setText(pitch.getStatus(), false);

            if (pitch.getLocation() != null) {
                dialogBinding.etPitchLat.setText(String.format(Locale.US, "%.6f", pitch.getLocation().getLatitude()));
                dialogBinding.etPitchLng.setText(String.format(Locale.US, "%.6f", pitch.getLocation().getLongitude()));
            }

            if (pitch.getImageUrls() != null && !pitch.getImageUrls().isEmpty()) {
                dialogBinding.etPitchImageUrl.setText(pitch.getImageUrls().get(0));
                dialogBinding.tvPickImageHint.setVisibility(View.GONE);
                dialogBinding.ivPitchPreview.setVisibility(View.VISIBLE);
                Glide.with(this).load(pitch.getImageUrls().get(0)).centerCrop().into(dialogBinding.ivPitchPreview);
            }
            setAmenityChips(pitch.getAmenities());
        } else {
            // Giá trị mặc định khi thêm sân mới
            dialogBinding.etPitchLat.setText("21.028511");
            dialogBinding.etPitchLng.setText("105.782302");
            dialogBinding.etPitchPrice.setText("200000");
            checkPermissionAndGetLocation();
        }

        dialogBinding.cardPickImage.setOnClickListener(v -> imagePickerLauncher.launch("image/*"));

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(pitch == null ? "🏟️ Thêm sân bóng cho thuê" : "Chỉnh sửa sân")
                .setView(dialogBinding.getRoot())
                .setPositiveButton("Lưu & Đăng sân", (d, w) -> savePitch())
                .setNegativeButton("Hủy", null)
                .setOnDismissListener(d -> dialogBinding = null)
                .show();
    }

    private void checkPermissionAndGetLocation() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            fillCurrentGpsLocation();
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
        }
    }

    private void fillCurrentGpsLocation() {
        try {
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                    .addOnSuccessListener(location -> {
                        if (location != null && dialogBinding != null) {
                            dialogBinding.etPitchLat.setText(String.format(Locale.US, "%.6f", location.getLatitude()));
                            dialogBinding.etPitchLng.setText(String.format(Locale.US, "%.6f", location.getLongitude()));
                            Snackbar.make(binding.getRoot(), "📍 Đã cập nhật tọa độ GPS thực tế!", Snackbar.LENGTH_SHORT).show();
                        }
                    });
        } catch (SecurityException ignored) {
        }
    }

    private void savePitch() {
        if (dialogBinding == null) return;
        String name       = getText(dialogBinding.etPitchName);
        String address    = getText(dialogBinding.etPitchAddress);
        String priceStr   = getText(dialogBinding.etPitchPrice);
        String latStr     = getText(dialogBinding.etPitchLat);
        String lngStr     = getText(dialogBinding.etPitchLng);
        String imageUrl   = getText(dialogBinding.etPitchImageUrl);
        String type       = dialogBinding.actPitchType.getText().toString();
        String status     = dialogBinding.actPitchStatus.getText().toString();
        String description= getText(dialogBinding.etPitchDescription);

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

        // Tọa độ GPS
        double lat = 21.028511;
        double lng = 105.782302;
        try {
            if (!latStr.isEmpty()) lat = Double.parseDouble(latStr);
            if (!lngStr.isEmpty()) lng = Double.parseDouble(lngStr);
        } catch (NumberFormatException ignored) {}

        Pitch pitch = editingPitch != null ? editingPitch : new Pitch();
        pitch.setName(name);
        pitch.setAddress(address);
        pitch.setBasePrice(price);
        pitch.setType(type.isEmpty() ? "5" : type);
        pitch.setStatus(status.isEmpty() ? Constants.PITCH_AVAILABLE : status);
        pitch.setDescription(description);
        pitch.setLocation(new GeoPoint(lat, lng));
        pitch.setAmenities(getSelectedAmenities());

        // Thiết lập hình ảnh sân
        if (!imageUrl.isEmpty()) {
            pitch.setImageUrls(new ArrayList<>(Collections.singletonList(imageUrl)));
        } else if (pitch.getImageUrls() == null || pitch.getImageUrls().isEmpty()) {
            pitch.setImageUrls(new ArrayList<>(Collections.singletonList(DEFAULT_PITCH_IMAGE)));
        }

        if (editingPitch == null) {
            ownerViewModel.createPitch(pitch);
        } else {
            ownerViewModel.updatePitch(pitch);
            if (selectedImageUri != null) {
                ownerViewModel.uploadPitchImage(pitch.getPitchId(), selectedImageUri);
            }
        }
    }

    private void setAmenityChips(List<String> amenities) {
        if (amenities == null) return;
        if (amenities.contains("🅿️ Bãi đỗ xe")) dialogBinding.chipParking.setChecked(true);
        if (amenities.contains("🚿 Phòng tắm"))  dialogBinding.chipShower.setChecked(true);
        if (amenities.contains("💡 Đèn đêm"))    dialogBinding.chipLight.setChecked(true);
        if (amenities.contains("🏠 Mái che"))    dialogBinding.chipCover.setChecked(true);
        if (amenities.contains("💧 Nước uống"))  dialogBinding.chipWater.setChecked(true);
    }

    private List<String> getSelectedAmenities() {
        List<String> list = new ArrayList<>();
        if (dialogBinding.chipParking.isChecked()) list.add("🅿️ Bãi đỗ xe");
        if (dialogBinding.chipShower.isChecked())  list.add("🚿 Phòng tắm");
        if (dialogBinding.chipLight.isChecked())   list.add("💡 Đèn đêm");
        if (dialogBinding.chipCover.isChecked())   list.add("🏠 Mái che");
        if (dialogBinding.chipWater.isChecked())   list.add("💧 Nước uống");
        return list;
    }

    private String getText(com.google.android.material.textfield.TextInputEditText et) {
        return et.getText() != null ? et.getText().toString().trim() : "";
    }

    @Override
    public void onEdit(Pitch pitch) {
        openAddEditDialog(pitch);
    }

    @Override
    public void onDelete(Pitch pitch) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Đóng sân?")
                .setMessage("Sân \"" + pitch.getName() + "\" sẽ bị ẩn khỏi danh sách và bản đồ thuê.")
                .setPositiveButton("Đóng sân", (d, w) -> ownerViewModel.closePitch(pitch.getPitchId()))
                .setNegativeButton("Hủy", null)
                .show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
