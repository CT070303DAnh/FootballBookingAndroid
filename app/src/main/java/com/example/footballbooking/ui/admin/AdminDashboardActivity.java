package com.example.footballbooking.ui.admin;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.example.footballbooking.R;
import com.example.footballbooking.databinding.ActivityAdminDashboardBinding;
import com.example.footballbooking.ui.auth.LoginActivity;
import com.google.firebase.auth.FirebaseAuth;

/**
 * AdminDashboardActivity — Màn hình chính của Admin.
 *
 * Navigation: NavController + BottomNavigationView
 * Fragments: AdminBooking | AdminPitch | AdminReport | AdminStaff
 *
 * PHÂN QUYỀN: Chỉ user có role = "admin" mới được navigate đến đây.
 */
public class AdminDashboardActivity extends AppCompatActivity {

    private ActivityAdminDashboardBinding binding;
    private NavController navController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAdminDashboardBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        applySystemBarInsets();
        setupNavigation();
        setupLogout();
    }

    /**
     * Android 15+ dùng edge-to-edge mặc định với target SDK mới.
     * Cộng inset status/navigation bar để AppBar và nút logout không bị che.
     */
    private void applySystemBarInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.appBarAdmin, (view, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.statusBars());
            view.setPadding(view.getPaddingLeft(), systemBars.top,
                    view.getPaddingRight(), view.getPaddingBottom());
            return insets;
        });

        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomNavAdmin, (view, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars());
            view.setPadding(view.getPaddingLeft(), view.getPaddingTop(),
                    view.getPaddingRight(), systemBars.bottom);
            return insets;
        });

        ViewCompat.requestApplyInsets(binding.getRoot());
    }

    private void setupNavigation() {
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_admin);

        if (navHostFragment != null) {
            navController = navHostFragment.getNavController();
            NavigationUI.setupWithNavController(
                    binding.bottomNavAdmin, navController);
        }
    }

    private void setupLogout() {
        binding.btnAdminLogoutTop.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Đăng xuất")
                    .setMessage("Bạn có chắc chắn muốn đăng xuất tài khoản Quản trị viên?")
                    .setPositiveButton("Đăng xuất", (dialog, which) -> {
                        FirebaseAuth.getInstance().signOut();
                        Intent intent = new Intent(this, LoginActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    })
                    .setNegativeButton("Hủy", null)
                    .show();
        });
    }

    @Override
    public boolean onSupportNavigateUp() {
        return navController != null && navController.navigateUp()
                || super.onSupportNavigateUp();
    }
}
