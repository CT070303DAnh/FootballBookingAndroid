package com.example.footballbooking.ui.owner.profile;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.footballbooking.data.model.User;
import com.example.footballbooking.databinding.FragmentOwnerProfileBinding;
import com.example.footballbooking.ui.owner.OwnerViewModel;
import com.google.firebase.auth.FirebaseAuth;

/**
 * OwnerProfileFragment — Hồ sơ chủ sân.
 * Hiển thị thông tin cá nhân, thống kê tổng quan và nút đăng xuất.
 */
public class OwnerProfileFragment extends Fragment {

    private FragmentOwnerProfileBinding binding;
    private OwnerViewModel ownerViewModel;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentOwnerProfileBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ownerViewModel = new ViewModelProvider(requireActivity()).get(OwnerViewModel.class);

        bindOwnerInfo();
        setupLogout();
    }

    private void bindOwnerInfo() {
        User owner = ownerViewModel.getCurrentOwner();
        if (owner == null) return;

        binding.tvOwnerProfileName.setText(owner.getDisplayName());
        binding.tvOwnerProfileEmail.setText(owner.getEmail());
        binding.tvOwnerProfilePhone.setText(owner.getPhoneNumber());
        binding.chipOwnerStatus.setText("✓ Chủ sân đã xác minh");
    }

    private void setupLogout() {
        binding.btnOwnerLogout.setOnClickListener(v ->
                new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                        .setTitle("Đăng xuất")
                        .setMessage("Bạn muốn đăng xuất khỏi tài khoản chủ sân?")
                        .setPositiveButton("Đăng xuất", (d, w) -> {
                            FirebaseAuth.getInstance().signOut();
                            requireActivity().finishAffinity();
                            startActivity(new android.content.Intent(requireContext(),
                                    com.example.footballbooking.ui.auth.LoginActivity.class));
                        })
                        .setNegativeButton("Hủy", null)
                        .show());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
