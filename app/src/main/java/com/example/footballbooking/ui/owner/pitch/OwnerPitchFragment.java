package com.example.footballbooking.ui.owner.pitch;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.footballbooking.data.model.Pitch;
import com.example.footballbooking.data.model.User;
import com.example.footballbooking.databinding.FragmentOwnerPitchBinding;
import com.example.footballbooking.ui.common.adapter.AdminPitchAdapter;
import com.example.footballbooking.ui.owner.OwnerViewModel;
import com.example.footballbooking.utils.Constants;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.util.Calendar;
import java.util.List;

/**
 * OwnerPitchFragment — Quản lý và Thêm sân bóng của chủ sân.
 */
public class OwnerPitchFragment extends Fragment
        implements AdminPitchAdapter.OnAdminPitchActionListener {

    private FragmentOwnerPitchBinding binding;
    private OwnerViewModel ownerViewModel;
    private AdminPitchAdapter pitchAdapter;

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
        binding.fabAddMyPitch.setOnClickListener(v -> openPitchAddEditDialog(null));
        binding.btnHeaderAddPitch.setOnClickListener(v -> openPitchAddEditDialog(null));
        binding.btnEmptyAddPitch.setOnClickListener(v -> openPitchAddEditDialog(null));
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
                Snackbar.make(binding.getRoot(), "🎉 Đã lưu sân \"" + resource.data.getName() + "\" thành công!", Snackbar.LENGTH_LONG).show();
            } else if (resource.isError()) {
                Snackbar.make(binding.getRoot(), "Lỗi thêm sân: " + resource.message, Snackbar.LENGTH_LONG).show();
            }
        });
    }

    private void updateKpiStats(List<Pitch> pitches) {
        binding.tvStatPitches.setText(String.valueOf(pitches.size()));
    }

    private void openPitchAddEditDialog(@Nullable Pitch pitch) {
        String pitchId = pitch != null ? pitch.getPitchId() : null;
        PitchAddEditDialogFragment.newInstance(pitchId).show(getChildFragmentManager(), "PitchAddEdit");
    }

    @Override
    public void onEdit(Pitch pitch) {
        openPitchAddEditDialog(pitch);
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
