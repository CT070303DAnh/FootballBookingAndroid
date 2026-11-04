package com.example.footballbooking.ui.customer;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.OnBackPressedCallback;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.example.footballbooking.R;
import com.example.footballbooking.data.repository.ChatRepository;
import com.example.footballbooking.databinding.ActivityCustomerMainBinding;
import com.example.footballbooking.ui.auth.LoginActivity;
import com.google.android.material.badge.BadgeDrawable;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;

/**
 * CustomerMainActivity — Activity chứa BottomNavigationView + NavHostFragment.
 *
 * THIẾT KẾ:
 * - Đây là Activity DUY NHẤT của phía Customer sau khi login.
 * - Tất cả màn hình Customer là Fragment, navigate bằng Navigation Component.
 * - PitchViewModel được khởi tạo ở scope Activity → shared giữa các Fragment.
 *
 * KHÔNG đặt bất kỳ business logic nào ở đây — chỉ quản lý navigation.
 */
public class CustomerMainActivity extends AppCompatActivity {

    private ActivityCustomerMainBinding binding;
    private NavController navController;
    private ListenerRegistration unreadMessagesListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCustomerMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        applySystemBarInsets();
        setupNavigation();
        setupBackNavigation();
    }

    private void applySystemBarInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomNav, (view, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars());
            view.setPadding(view.getPaddingLeft(), view.getPaddingTop(),
                    view.getPaddingRight(), systemBars.bottom);
            return insets;
        });

        ViewCompat.requestApplyInsets(binding.getRoot());
    }

    private void setupNavigation() {
        // Lấy NavController từ NavHostFragment
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);

        if (navHostFragment == null) return;
        navController = navHostFragment.getNavController();

        // Kết nối BottomNavigationView với NavController
        // NavigationUI tự động xử lý: tab click → navigate, back stack...
        NavigationUI.setupWithNavController(binding.bottomNav, navController);

        // Ẩn/hiện bottom nav theo destination (nếu cần)
        navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
            // Hiện bottom nav ở tất cả các tab chính
            binding.bottomNav.setVisibility(android.view.View.VISIBLE);
        });
    }

    /**
     * Xử lý nút Back của hệ thống.
     * Nếu đang ở home tab → không back về Login, hỏi thoát app.
     */
    private void setupBackNavigation() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
        if (navController != null && navController.getCurrentDestination() != null
                && navController.getCurrentDestination().getId() == R.id.nav_home) {
            // Đang ở Home → hỏi thoát app
            new androidx.appcompat.app.AlertDialog.Builder(CustomerMainActivity.this)
                    .setTitle("Thoát ứng dụng")
                    .setMessage("Bạn có muốn thoát không?")
                    .setPositiveButton("Thoát", (d, w) -> finish())
                    .setNegativeButton("Ở lại", null)
                    .show();
                    return;
        } else {
                    if (navController == null || !navController.navigateUp()) {
                        finish();
                    }
        }
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        startUnreadMessagesListener();
    }

    @Override
    protected void onStop() {
        stopUnreadMessagesListener();
        super.onStop();
    }

    private void startUnreadMessagesListener() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        unreadMessagesListener = new ChatRepository().listenUnreadCount(user.getUid(),
                (count, error) -> renderUnreadBadge(error == null && count != null ? count : 0));
    }

    private void stopUnreadMessagesListener() {
        if (unreadMessagesListener != null) {
            unreadMessagesListener.remove();
            unreadMessagesListener = null;
        }
    }

    private void renderUnreadBadge(int count) {
        if (binding == null || binding.bottomNav == null) return;
        BadgeDrawable badge = binding.bottomNav.getOrCreateBadge(R.id.nav_chat);
        if (count > 0) {
            badge.setVisible(true);
            badge.setNumber(count);
        } else {
            badge.clearNumber();
            badge.setVisible(false);
        }
    }

    /** Chuyển tab trên BottomNavigationView một cách chuẩn xác */
    public void selectBottomNavTab(int menuItemId) {
        if (binding != null && binding.bottomNav != null) {
            binding.bottomNav.setSelectedItemId(menuItemId);
        }
    }

    /** Điều hướng về Login khi logout (gọi từ ProfileFragment) */
    public void navigateToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
