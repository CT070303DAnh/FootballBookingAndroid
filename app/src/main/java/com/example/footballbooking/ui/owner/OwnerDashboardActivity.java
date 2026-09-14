package com.example.footballbooking.ui.owner;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.example.footballbooking.R;
import com.example.footballbooking.data.model.User;
import com.example.footballbooking.data.repository.AuthRepository;
import com.example.footballbooking.databinding.ActivityOwnerDashboardBinding;
import com.example.footballbooking.utils.Constants;
import com.google.android.material.snackbar.Snackbar;

/**
 * OwnerDashboardActivity — Màn hình chính của Chủ sân.
 *
 * KHỞI TẠO:
 * 1. Load User từ Firestore (để có displayName, phone cho OwnerViewModel)
 * 2. Gọi ownerViewModel.init(user) để khởi động realtime listeners
 * 3. Kết nối NavController + BottomNav
 *
 * PHÂN QUYỀN: Chỉ user có role = "owner" mới đến được đây.
 */
public class OwnerDashboardActivity extends AppCompatActivity {

    private ActivityOwnerDashboardBinding binding;
    private NavController navController;
    private OwnerViewModel ownerViewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityOwnerDashboardBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ownerViewModel = new ViewModelProvider(this).get(OwnerViewModel.class);

        loadCurrentUserAndInit();
        setupNavigation();
        setupLogout();
    }

    private void setupLogout() {
        binding.btnOwnerLogoutTop.setOnClickListener(v -> {
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Đăng xuất")
                    .setMessage("Bạn có chắc chắn muốn đăng xuất tài khoản Chủ sân?")
                    .setPositiveButton("Đăng xuất", (dialog, which) -> {
                        com.google.firebase.auth.FirebaseAuth.getInstance().signOut();
                        android.content.Intent intent = new android.content.Intent(this,
                                com.example.footballbooking.ui.auth.LoginActivity.class);
                        intent.setFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    })
                    .setNegativeButton("Hủy", null)
                    .show();
        });
    }

    /**
     * Load thông tin User hiện tại từ Firestore, sau đó init ViewModel.
     */
    private void loadCurrentUserAndInit() {
        AuthRepository.getInstance().getCurrentUserProfile(userResource -> {
            if (userResource != null && userResource.isSuccess() && userResource.data != null) {
                User currentUser = userResource.data;

                // Kiểm tra role lần cuối (defense-in-depth)
                if (!Constants.ROLE_OWNER.equals(currentUser.getRole())) {
                    Snackbar.make(binding.getRoot(),
                            "Không có quyền truy cập", Snackbar.LENGTH_LONG).show();
                    finish();
                    return;
                }

                // Khởi động ViewModel với context của owner
                ownerViewModel.init(currentUser);
            }
        });
    }

    private void setupNavigation() {
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_owner);

        if (navHostFragment != null) {
            navController = navHostFragment.getNavController();
            NavigationUI.setupWithNavController(binding.bottomNavOwner, navController);
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        return navController != null && navController.navigateUp()
                || super.onSupportNavigateUp();
    }
}
