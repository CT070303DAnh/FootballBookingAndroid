package com.example.footballbooking.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.view.animation.AccelerateDecelerateInterpolator;
import com.example.footballbooking.ui.customer.CustomerMainActivity;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.footballbooking.databinding.ActivitySplashBinding;
import com.example.footballbooking.utils.Constants;
import com.example.footballbooking.utils.FirestoreSeeder;

/**
 * SplashActivity — Màn hình khởi động.
 *
 * FLOW:
 * 1. Hiển thị animation logo
 * 2. Kiểm tra session Firebase Auth
 * 3a. Đã đăng nhập → Navigate theo role (Customer/Admin)
 * 3b. Chưa đăng nhập → Navigate đến LoginActivity
 */
public class SplashActivity extends AppCompatActivity {

    private ActivitySplashBinding binding;
    private AuthViewModel authViewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // FirestoreSeeder.seedAll();
        binding = ActivitySplashBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Khởi tạo ViewModel — chia sẻ với các Auth screen
        authViewModel = new ViewModelProvider(this).get(AuthViewModel.class);

        // Chạy animation logo trước
        playLogoAnimation();

        // Observe kết quả check session
        authViewModel.getCurrentUser().observe(this, resource -> {
            if (resource == null)
                return;

            switch (resource.status) {
                case LOADING:
                    // Đang kiểm tra — animation đã chạy rồi, không cần thêm
                    break;

                case SUCCESS:
                    // Delay nhỏ để animation hoàn thành đẹp
                    binding.getRoot().postDelayed(() -> {
                        if (resource.data == null) {
                            // Chưa đăng nhập
                            goToLogin();
                        } else {
                            // Đã đăng nhập → phân quyền
                            String role = resource.data.getRole();
                            if (Constants.ROLE_ADMIN.equals(role)) {
                                goToAdminDashboard();
                            } else if (Constants.ROLE_OWNER.equals(role)) {
                                goToOwnerDashboard();
                            } else {
                                goToCustomerHome();
                            }
                        }
                    }, 600); // delay 600ms cho animation
                    break;

                case ERROR:
                    // Lỗi kết nối hoặc tài khoản bị chặn → vẫn về Login
                    binding.getRoot().postDelayed(() -> {
                        if (resource.message != null && resource.message.contains("đình chỉ")) {
                            // Nếu lỗi do bị chặn, truyền message sang Login để hiển thị
                            Intent intent = new Intent(SplashActivity.this, LoginActivity.class);
                            intent.putExtra("ERROR_MESSAGE", resource.message);
                            startActivity(intent);
                            finish();
                        } else {
                            goToLogin();
                        }
                    }, 600);
                    break;
            }
        });

        // Trigger kiểm tra session (sau khi animation bắt đầu)
        binding.getRoot().postDelayed(
                () -> authViewModel.checkLoginSession(),
                800 // chờ 800ms để logo animation chạy trước
        );
    }

    /** Animation: Logo scale từ 0.8 lên 1.0 + fade in */
    private void playLogoAnimation() {
        // Logo: animate scale + alpha
        binding.layoutLogo.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(600)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .start();

        // Progress bar: fade in sau 400ms
        binding.progressSplash.postDelayed(() -> binding.progressSplash.animate()
                .alpha(1f)
                .setDuration(300)
                .start(),
                400);
    }

    private void goToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        startActivity(intent);
        finish(); // Đóng SplashActivity, không back lại được
    }

    private void goToCustomerHome() {
        Intent intent = new Intent(this, CustomerMainActivity.class);
        startActivity(intent);
        finish();
    }

    private void goToAdminDashboard() {
        Intent intent = new Intent(this, com.example.footballbooking.ui.admin.AdminDashboardActivity.class);
        startActivity(intent);
        finish();
    }

    private void goToOwnerDashboard() {
        Intent intent = new Intent(this, com.example.footballbooking.ui.owner.OwnerDashboardActivity.class);
        startActivity(intent);
        finish();
    }
}
