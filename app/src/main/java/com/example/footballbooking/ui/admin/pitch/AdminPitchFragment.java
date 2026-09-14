package com.example.footballbooking.ui.admin.pitch;

import android.app.Activity;
import android.content.Intent;
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
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.bumptech.glide.Glide;
import com.example.footballbooking.data.model.Pitch;
import com.example.footballbooking.databinding.DialogAddEditPitchBinding;
import com.example.footballbooking.databinding.FragmentAdminPitchBinding;
import com.example.footballbooking.ui.admin.AdminViewModel;
import com.example.footballbooking.ui.common.adapter.AdminPitchAdapter;
import com.example.footballbooking.utils.Constants;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * AdminPitchFragment — CRUD Sân bóng.
 *
 * TÍNH NĂNG:
 * - Xem danh sách sân (realtime)
 * - Thêm sân mới với Dialog
 * - Chỉnh sửa thông tin sân
 * - Xóa sân (soft delete → status = closed)
 * - Upload ảnh sân lên Firebase Storage
 */
public class AdminPitchFragment extends Fragment
        implements AdminPitchAdapter.OnAdminPitchActionListener {

    private FragmentAdminPitchBinding binding;
    private AdminViewModel adminViewModel;
    private AdminPitchAdapter pitchAdapter;

    // Dialog binding (lưu để truy cập trong callback upload)
    private DialogAddEditPitchBinding dialogBinding;
    private Uri selectedImageUri;
    private Pitch editingPitch; // null = thêm mới, non-null = chỉnh sửa

    private final ActivityResultLauncher<String> imagePickerLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.GetContent(),
                    uri -> {
                        if (uri != null) {
                            selectedImageUri = uri;
                            if (dialogBinding != null) {
                                dialogBinding.tvPickImageHint.setVisibility(View.GONE);
                                dialogBinding.ivPitchPreview.setVisibility(View.VISIBLE);
                                Glide.with(this).load(uri).centerCrop()
                                        .into(dialogBinding.ivPitchPreview);
                            }
                        }
                    });

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentAdminPitchBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        adminViewModel = new ViewModelProvider(requireActivity()).get(AdminViewModel.class);

        setupRecyclerView();
        setupFab();
        observeViewModel();
        adminViewModel.loadAllPitches();
    }

    private void setupRecyclerView() {
        pitchAdapter = new AdminPitchAdapter(this);
        binding.rvAdminPitches.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvAdminPitches.setAdapter(pitchAdapter);
    }

    private void setupFab() {
        binding.fabAddPitch.setOnClickListener(v -> openAddEditDialog(null));
    }

    private void observeViewModel() {
        adminViewModel.getAllPitches().observe(getViewLifecycleOwner(), resource -> {
            if (resource == null) return;
            binding.progressAdminPitch.setVisibility(
                    resource.isLoading() ? View.VISIBLE : View.GONE);
            if (resource.isSuccess() && resource.data != null) {
                pitchAdapter.submitList(resource.data);
            } else if (resource.isError()) {
                Snackbar.make(binding.getRoot(), resource.message, Snackbar.LENGTH_LONG).show();
            }
        });

        adminViewModel.getActionResult().observe(getViewLifecycleOwner(), resource -> {
            if (resource == null) return;
            if (resource.isSuccess()) {
                Snackbar.make(binding.getRoot(), "✅ Đã cập nhật sân", Snackbar.LENGTH_SHORT).show();
            } else if (resource.isError()) {
                Snackbar.make(binding.getRoot(), "Lỗi: " + resource.message, Snackbar.LENGTH_LONG).show();
            }
        });

        adminViewModel.getUploadResult().observe(getViewLifecycleOwner(), resource -> {
            if (resource == null || dialogBinding == null) return;
            if (resource.isLoading()) {
                dialogBinding.progressUpload.setVisibility(View.VISIBLE);
            } else {
                dialogBinding.progressUpload.setVisibility(View.GONE);
                if (resource.isSuccess()) {
                    Snackbar.make(binding.getRoot(), "✅ Tải ảnh thành công", Snackbar.LENGTH_SHORT).show();
                }
            }
        });

        adminViewModel.getPitchResult().observe(getViewLifecycleOwner(), resource -> {
            if (resource == null) return;
            if (resource.isSuccess() && resource.data != null && selectedImageUri != null) {
                // Sau khi tạo sân xong, upload ảnh
                adminViewModel.uploadPitchImage(resource.data.getPitchId(), selectedImageUri);
            }
        });
    }

    // ============================================================
    // ADD / EDIT DIALOG
    // ============================================================

    private void openAddEditDialog(@Nullable Pitch pitch) {
        this.editingPitch  = pitch;
        this.selectedImageUri = null;

        dialogBinding = DialogAddEditPitchBinding.inflate(LayoutInflater.from(requireContext()));

        // Setup dropdown: Loại sân
        String[] types = {"5", "7", "11"};
        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_dropdown_item_1line, types);
        dialogBinding.actPitchType.setAdapter(typeAdapter);

        // Setup dropdown: Trạng thái
        String[] statuses = {Constants.PITCH_AVAILABLE, Constants.PITCH_MAINTENANCE, Constants.PITCH_CLOSED};
        ArrayAdapter<String> statusAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_dropdown_item_1line, statuses);
        dialogBinding.actPitchStatus.setAdapter(statusAdapter);

        // Điền dữ liệu nếu là chỉnh sửa
        if (pitch != null) {
            dialogBinding.etPitchName.setText(pitch.getName());
            dialogBinding.etPitchAddress.setText(pitch.getAddress());
            dialogBinding.etPitchPrice.setText(String.valueOf((long) pitch.getBasePrice()));
            dialogBinding.etPitchDescription.setText(pitch.getDescription());
            dialogBinding.actPitchType.setText(pitch.getType(), false);
            dialogBinding.actPitchStatus.setText(pitch.getStatus(), false);

            // Ảnh hiện tại
            if (pitch.getImageUrls() != null && !pitch.getImageUrls().isEmpty()) {
                dialogBinding.tvPickImageHint.setVisibility(View.GONE);
                dialogBinding.ivPitchPreview.setVisibility(View.VISIBLE);
                Glide.with(this).load(pitch.getImageUrls().get(0)).centerCrop()
                        .into(dialogBinding.ivPitchPreview);
            }

            // Amenities
            if (pitch.getAmenities() != null) {
                setAmenityChips(pitch.getAmenities());
            }
        }

        // Tap để chọn ảnh
        dialogBinding.cardPickImage.setOnClickListener(v ->
                imagePickerLauncher.launch("image/*"));

        String title = (pitch == null) ? "Thêm sân mới" : "Chỉnh sửa sân";

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(title)
                .setView(dialogBinding.getRoot())
                .setPositiveButton("Lưu", (d, w) -> savePitch())
                .setNegativeButton("Hủy", null)
                .setOnDismissListener(d -> dialogBinding = null)
                .show();
    }

    private void savePitch() {
        if (dialogBinding == null) return;

        String name = getText(dialogBinding.etPitchName);
        String address = getText(dialogBinding.etPitchAddress);
        String priceStr = getText(dialogBinding.etPitchPrice);
        String type = dialogBinding.actPitchType.getText().toString();
        String status = dialogBinding.actPitchStatus.getText().toString();
        String description = getText(dialogBinding.etPitchDescription);

        if (name.isEmpty() || address.isEmpty() || priceStr.isEmpty()) {
            Snackbar.make(binding.getRoot(), "Vui lòng điền đầy đủ thông tin bắt buộc (*)",
                    Snackbar.LENGTH_SHORT).show();
            return;
        }

        double price;
        try { price = Double.parseDouble(priceStr); }
        catch (NumberFormatException e) {
            Snackbar.make(binding.getRoot(), "Giá không hợp lệ", Snackbar.LENGTH_SHORT).show();
            return;
        }

        // Build Pitch object
        Pitch pitch = editingPitch != null ? editingPitch : new Pitch();
        pitch.setName(name);
        pitch.setAddress(address);
        pitch.setBasePrice(price);
        pitch.setType(type.isEmpty() ? "5" : type);
        pitch.setStatus(status.isEmpty() ? Constants.PITCH_AVAILABLE : status);
        pitch.setDescription(description);
        pitch.setAmenities(getSelectedAmenities());

        String latStr = getText(dialogBinding.etPitchLat);
        String lngStr = getText(dialogBinding.etPitchLng);
        double lat = 21.028511;
        double lng = 105.782302;
        try {
            if (!latStr.isEmpty()) lat = Double.parseDouble(latStr);
            if (!lngStr.isEmpty()) lng = Double.parseDouble(lngStr);
        } catch (NumberFormatException ignored) {}
        pitch.setLocation(new com.google.firebase.firestore.GeoPoint(lat, lng));

        String imageUrl = getText(dialogBinding.etPitchImageUrl);
        if (!imageUrl.isEmpty()) {
            pitch.setImageUrls(new ArrayList<>(java.util.Collections.singletonList(imageUrl)));
        } else if (pitch.getImageUrls() == null || pitch.getImageUrls().isEmpty()) {
            pitch.setImageUrls(new ArrayList<>(java.util.Collections.singletonList("https://images.unsplash.com/photo-1529900248674-20b72322c342?q=80&w=800")));
        }

        if (editingPitch == null) {
            adminViewModel.createPitch(pitch);
            // Upload ảnh sẽ được trigger trong observer getPitchResult()
        } else {
            adminViewModel.updatePitch(pitch);
            // Upload ảnh mới nếu user chọn
            if (selectedImageUri != null) {
                adminViewModel.uploadPitchImage(pitch.getPitchId(), selectedImageUri);
            }
        }
    }

    private void setAmenityChips(List<String> amenities) {
        if (amenities.contains("🅿️ Bãi đỗ xe"))  dialogBinding.chipParking.setChecked(true);
        if (amenities.contains("🚿 Phòng tắm"))   dialogBinding.chipShower.setChecked(true);
        if (amenities.contains("💡 Đèn đêm"))     dialogBinding.chipLight.setChecked(true);
        if (amenities.contains("🏠 Mái che"))     dialogBinding.chipCover.setChecked(true);
        if (amenities.contains("💧 Nước uống"))   dialogBinding.chipWater.setChecked(true);
    }

    private List<String> getSelectedAmenities() {
        List<String> amenities = new ArrayList<>();
        if (dialogBinding.chipParking.isChecked()) amenities.add("🅿️ Bãi đỗ xe");
        if (dialogBinding.chipShower.isChecked())  amenities.add("🚿 Phòng tắm");
        if (dialogBinding.chipLight.isChecked())   amenities.add("💡 Đèn đêm");
        if (dialogBinding.chipCover.isChecked())   amenities.add("🏠 Mái che");
        if (dialogBinding.chipWater.isChecked())   amenities.add("💧 Nước uống");
        return amenities;
    }

    private String getText(com.google.android.material.textfield.TextInputEditText et) {
        return et.getText() != null ? et.getText().toString().trim() : "";
    }

    // ============================================================
    // ADAPTER CALLBACKS
    // ============================================================

    @Override
    public void onEdit(Pitch pitch) { openAddEditDialog(pitch); }

    @Override
    public void onDelete(Pitch pitch) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Đóng sân?")
                .setMessage("Sân \"" + pitch.getName() + "\" sẽ bị ẩn khỏi danh sách.\n"
                        + "Dữ liệu đặt sân cũ vẫn được giữ nguyên.")
                .setPositiveButton("Đóng sân", (d, w) ->
                        adminViewModel.deletePitch(pitch.getPitchId()))
                .setNegativeButton("Hủy", null)
                .show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
