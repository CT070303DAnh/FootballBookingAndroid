package com.example.footballbooking.ui.customer.profile;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.bumptech.glide.Glide;
import com.example.footballbooking.R;
import com.example.footballbooking.data.model.User;
import com.example.footballbooking.data.repository.AuthRepository;
import com.example.footballbooking.databinding.FragmentProfileBinding;
import com.example.footballbooking.ui.auth.LoginActivity;
import com.example.footballbooking.ui.customer.CustomerMainActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * ProfileFragment — Màn hình Hồ sơ cá nhân của Customer.
 * Hiển thị thông tin cá nhân, liên kết các màn hình tiện ích,
 * và hỗ trợ đăng xuất an toàn khỏi hệ thống.
 */
public class ProfileFragment extends Fragment {

    private FragmentProfileBinding binding;
    private final AuthRepository authRepository = AuthRepository.getInstance();

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentProfileBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        loadUserProfile();
        setupClickListeners();
    }

    private void loadUserProfile() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            // Hiển thị fallback từ FirebaseUser trước
            if (currentUser.getDisplayName() != null && !currentUser.getDisplayName().isEmpty()) {
                binding.tvProfileName.setText(currentUser.getDisplayName());
            }
            binding.tvProfileEmail.setText(currentUser.getEmail());
            if (currentUser.getPhoneNumber() != null && !currentUser.getPhoneNumber().isEmpty()) {
                binding.tvProfilePhone.setText(currentUser.getPhoneNumber());
            } else {
                binding.tvProfilePhone.setText("Chưa cập nhật");
            }
        }

        // Tải thông tin chi tiết đầy đủ từ Firestore
        authRepository.getCurrentUserProfile(resource -> {
            if (resource != null && resource.isSuccess() && resource.data != null && isAdded()) {
                User user = resource.data;
                if (user.getDisplayName() != null && !user.getDisplayName().isEmpty()) {
                    binding.tvProfileName.setText(user.getDisplayName());
                }
                if (user.getEmail() != null) {
                    binding.tvProfileEmail.setText(user.getEmail());
                }
                if (user.getPhoneNumber() != null && !user.getPhoneNumber().isEmpty()) {
                    binding.tvProfilePhone.setText(user.getPhoneNumber());
                }

                // Avatar
                if (user.getAvatarUrl() != null && !user.getAvatarUrl().isEmpty()) {
                    Glide.with(this)
                            .load(user.getAvatarUrl())
                            .placeholder(android.R.drawable.ic_menu_myplaces)
                            .error(android.R.drawable.ic_menu_myplaces)
                            .into(binding.ivProfileAvatar);
                }
            }
        });
    }

    private void setupClickListeners() {
        // Mở lịch sử đặt sân
        binding.layoutMenuHistory.setOnClickListener(v -> navigateToTab(R.id.nav_history));

        // Mở AI Gợi ý
        binding.layoutMenuAi.setOnClickListener(v -> navigateToTab(R.id.nav_ai));

        // Mở Bản đồ sân bóng
        binding.layoutMenuMap.setOnClickListener(v -> navigateToTab(R.id.nav_map));

        // Nút Đăng xuất
        binding.btnLogout.setOnClickListener(v -> showLogoutConfirmationDialog());
    }

    private void navigateToTab(int tabId) {
        if (getActivity() instanceof CustomerMainActivity) {
            ((CustomerMainActivity) getActivity()).selectBottomNavTab(tabId);
        } else {
            Navigation.findNavController(requireView()).navigate(tabId);
        }
    }

    private void showLogoutConfirmationDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Đăng xuất")
                .setMessage("Bạn có chắc chắn muốn đăng xuất tài khoản không?")
                .setPositiveButton("Đăng xuất", (dialog, which) -> {
                    authRepository.logout();
                    if (getActivity() instanceof CustomerMainActivity) {
                        ((CustomerMainActivity) getActivity()).navigateToLogin();
                    } else {
                        Intent intent = new Intent(requireActivity(), LoginActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        requireActivity().finish();
                    }
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
