package com.example.footballbooking.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.text.Html;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.footballbooking.databinding.ActivityLoginBinding;
import com.example.footballbooking.ui.customer.CustomerMainActivity;
import com.example.footballbooking.utils.Constants;
import com.google.android.material.snackbar.Snackbar;

/**
 * LoginActivity — Màn hình đăng nhập.
 *
 * PATTERN: Observe LiveData từ ViewModel.
 * Activity KHÔNG chứa business logic.
 * Tất cả validation và Firebase call nằm trong ViewModel/Repository.
 */
public class LoginActivity extends AppCompatActivity {

    private ActivityLoginBinding binding;
    private AuthViewModel authViewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Khởi tạo ViewModel
        authViewModel = new ViewModelProvider(this).get(AuthViewModel.class);

        setupUI();
        observeViewModel();
    }

    private void setupUI() {
        // Render HTML cho text "Chưa có tài khoản? Đăng ký ngay"
        binding.tvGoRegister.setText(Html.fromHtml(
                getString(com.example.footballbooking.R.string.text_no_account),
                Html.FROM_HTML_MODE_COMPACT));

        // Nút Đăng nhập
        binding.btnLogin.setOnClickListener(v -> attemptLogin());

        // Enter key trên keyboard cũng trigger login
        binding.etPassword.setOnEditorActionListener((v, actionId, event) -> {
            attemptLogin();
            return true;
        });

        // Điều hướng sang màn hình Đăng ký
        binding.tvGoRegister.setOnClickListener(v -> {
            startActivity(new Intent(this, RegisterActivity.class));
            overridePendingTransition(android.R.anim.slide_in_left,
                                       android.R.anim.slide_out_right);
        });

        // Quên mật khẩu (TODO: implement reset password)
        binding.tvForgotPassword.setOnClickListener(v ->
                Snackbar.make(binding.getRoot(),
                        "Tính năng đang phát triển",
                        Snackbar.LENGTH_SHORT).show()
        );
    }

    private void observeViewModel() {
        authViewModel.getAuthResult().observe(this, resource -> {
            if (resource == null) return;

            switch (resource.status) {
                case LOADING:
                    showLoading(true);
                    break;

                case SUCCESS:
                    showLoading(false);
                    if (resource.data != null) {
                        // Phân quyền điều hướng
                        navigateByRole(resource.data.getRole());
                    }
                    break;

                case ERROR:
                    showLoading(false);
                    showError(resource.message);
                    break;
            }
        });
    }

    private void attemptLogin() {
        // Clear error trước
        hideError();

        String email    = binding.etEmail.getText() != null
                          ? binding.etEmail.getText().toString().trim() : "";
        String password = binding.etPassword.getText() != null
                          ? binding.etPassword.getText().toString() : "";

        // Validate input ở ViewModel
        String validationError = authViewModel.validateLoginInput(email, password);
        if (validationError != null) {
            showError(validationError);
            return;
        }

        // Gọi login
        authViewModel.loginWithEmail(email, password);
    }

    private void navigateByRole(String role) {
        if (Constants.ROLE_ADMIN.equals(role)) {
            startActivity(new Intent(this,
                    com.example.footballbooking.ui.admin.AdminDashboardActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            finish();
        } else if (Constants.ROLE_OWNER.equals(role)) {
            startActivity(new Intent(this,
                    com.example.footballbooking.ui.owner.OwnerDashboardActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            finish();
        } else if (Constants.ROLE_OWNER_PENDING.equals(role)) {
            // Chủ sân chờ Admin duyệt — chưa vào được Dashboard
            showError("Tài khoản chủ sân đang chờ Admin xét duyệt.\nVui lòng thử lại sau 24h. 🕐");
        } else {
            // customer (mặc định)
            startActivity(new Intent(this, com.example.footballbooking.ui.customer.CustomerMainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            finish();
        }
    }

    private void showLoading(boolean isLoading) {
        binding.btnLogin.setEnabled(!isLoading);
        binding.progressLogin.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        binding.btnLogin.setText(isLoading ? "Đang đăng nhập…" : getString(
                com.example.footballbooking.R.string.btn_login));
    }

    private void showError(String message) {
        binding.tvError.setVisibility(View.VISIBLE);
        binding.tvError.setText(message);
        // Animate shake effect
        binding.tvError.animate()
                .translationX(-10f).setDuration(50)
                .withEndAction(() -> binding.tvError.animate()
                        .translationX(10f).setDuration(50)
                        .withEndAction(() -> binding.tvError.animate()
                                .translationX(0f).setDuration(50).start())
                        .start())
                .start();
    }

    private void hideError() {
        binding.tvError.setVisibility(View.GONE);
        binding.tvError.setText("");
        // Clear TextInputLayout errors
        binding.tilEmail.setError(null);
        binding.tilPassword.setError(null);
    }
}
