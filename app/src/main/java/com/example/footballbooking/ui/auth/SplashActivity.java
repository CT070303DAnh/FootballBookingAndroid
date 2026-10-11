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
import com.example.footballbooking.utils.BookingExpiryWorker;

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
    private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
    private boolean navigated;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // FirestoreSeeder.seedAll();
        binding = ActivitySplashBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Tự động từ chối các đơn pending đã quá giờ (chạy nền, không block UI)
        BookingExpiryWorker.autoRejectExpiredBookings(null);

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
                    handler.postDelayed(() -> {
                        if (isFinishing() || isDestroyed() || navigated) return;
                        if (resource.data == null) {
                            // Chưa đăng nhập
                            goToLogin();
                        } else {
                            // Đã đăng nhập → phân quyền
                            RoleDestination destination = RoleDestination.fromRole(resource.data.getRole());
                            if (destination == RoleDestination.ADMIN) {
                                goToAdminDashboard();
                            } else if (destination == RoleDestination.OWNER) {
                                goToOwnerDashboard();
                            } else if (destination == RoleDestination.CUSTOMER) {
                                goToCustomerHome();
                            } else {
                                com.example.footballbooking.data.repository.AuthRepository.getInstance().logout();
                                goToLogin(destination == RoleDestination.PENDING
                                        ? "Tài khoản chủ sân đang chờ Admin xét duyệt. Vui lòng đăng nhập lại sau khi được duyệt."
                                        : "Quyền tài khoản không hợp lệ. Vui lòng liên hệ quản trị viên.");
                            }
                        }
                    }, 600); // delay 600ms cho animation
                    break;

                case ERROR:
                    // Lỗi kết nối hoặc tài khoản bị chặn → vẫn về Login
                    handler.postDelayed(() -> {
                        if (isFinishing() || isDestroyed() || navigated) return;
                        goToLogin(resource.message);
                    }, 600);
                    break;
            }
        });

        // Trigger kiểm tra session (sau khi animation bắt đầu)
        handler.postDelayed(
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
        handler.postDelayed(() -> binding.progressSplash.animate()
                .alpha(1f)
                .setDuration(300)
                .start(),
                400);
    }

    private void goToLogin() {
        goToLogin(null);
    }

    private void goToLogin(String message) {
        navigated = true;
        Intent intent = new Intent(this, LoginActivity.class);
        if (message != null) intent.putExtra("ERROR_MESSAGE", message);
        startActivity(intent);
        finish(); // Đóng SplashActivity, không back lại được
    }

    private void goToCustomerHome() {
        navigated = true;
        Intent intent = new Intent(this, CustomerMainActivity.class);
        startActivity(intent);
        finish();
    }

    private void goToAdminDashboard() {
        navigated = true;
        Intent intent = new Intent(this, com.example.footballbooking.ui.admin.AdminDashboardActivity.class);
        startActivity(intent);
        finish();
    }

    private void goToOwnerDashboard() {
        navigated = true;
        Intent intent = new Intent(this, com.example.footballbooking.ui.owner.OwnerDashboardActivity.class);
        startActivity(intent);
        finish();
    }

    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        binding.layoutLogo.animate().cancel();
        binding.progressSplash.animate().cancel();
        super.onDestroy();
        binding = null;
    }
}
