package com.example.footballbooking.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.text.Html;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.footballbooking.databinding.ActivityRegisterBinding;

/**
 * RegisterActivity — Màn hình đăng ký tài khoản Customer.
 */
public class RegisterActivity extends AppCompatActivity {

    private ActivityRegisterBinding binding;
    private AuthViewModel authViewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRegisterBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        authViewModel = new ViewModelProvider(this).get(AuthViewModel.class);

        setupUI();
        observeViewModel();
    }

    private void setupUI() {
        // Render HTML text
        binding.tvGoLogin.setText(Html.fromHtml(
                getString(com.example.footballbooking.R.string.text_have_account),
                Html.FROM_HTML_MODE_COMPACT));

        // Nút Back
        binding.btnBack.setOnClickListener(v -> {
            finish();
            overridePendingTransition(android.R.anim.slide_in_left,
                                       android.R.anim.slide_out_right);
        });

        // Enter key trên confirm password → trigger register
        binding.etConfirmPassword.setOnEditorActionListener((v, actionId, event) -> {
            attemptRegister();
            return true;
        });

        // Nút Đăng ký
        binding.btnRegister.setOnClickListener(v -> attemptRegister());

        // Điều hướng về Login
        binding.tvGoLogin.setOnClickListener(v -> {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });
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
                        // Đăng ký thành công → chuyển thẳng vào app Customer
                        Intent intent = new Intent(this, LoginActivity.class);
                        // TODO: thay bằng CustomerMainActivity.class
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                                        Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                    }
                    break;

                case ERROR:
                    showLoading(false);
                    showError(resource.message);
                    break;
            }
        });
    }

    private void attemptRegister() {
        hideError();

        String displayName      = getText(binding.etDisplayName);
        String phone            = getText(binding.etPhone);
        String email            = getText(binding.etEmail);
        String password         = getText(binding.etPassword);
        String confirmPassword  = getText(binding.etConfirmPassword);

        // Validate ở ViewModel
        String error = authViewModel.validateRegisterInput(
                email, password, confirmPassword, displayName, phone);

        if (error != null) {
            showError(error);
            return;
        }

        authViewModel.register(email, password, displayName, phone);
    }

    /** Helper: lấy text an toàn từ EditText */
    private String getText(android.widget.EditText et) {
        return et.getText() != null ? et.getText().toString().trim() : "";
    }

    private void showLoading(boolean isLoading) {
        binding.btnRegister.setEnabled(!isLoading);
        binding.progressRegister.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        binding.btnRegister.setText(isLoading ? "Đang tạo tài khoản…"
                : getString(com.example.footballbooking.R.string.btn_register));
    }

    private void showError(String message) {
        binding.tvError.setVisibility(View.VISIBLE);
        binding.tvError.setText(message);
    }

    private void hideError() {
        binding.tvError.setVisibility(View.GONE);
    }
}
