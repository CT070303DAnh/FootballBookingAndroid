package com.example.footballbooking.ui.auth;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.footballbooking.data.model.User;
import com.example.footballbooking.data.repository.AuthRepository;
import com.example.footballbooking.utils.Resource;

/**
 * AuthViewModel — ViewModel cho màn hình Auth (Login, Register, Splash).
 *
 * MVVM PATTERN:
 * - View (Activity/Fragment) observe LiveData từ ViewModel
 * - ViewModel GỌI Repository, KHÔNG biết về Firebase hay View
 * - Repository xử lý business logic & data
 *
 * ViewModel tồn tại qua configuration changes (xoay màn hình).
 */
public class AuthViewModel extends ViewModel {

    private final AuthRepository authRepository;

    // LiveData để các màn hình Auth observe
    // MutableLiveData private — expose qua getter bất biến (LiveData)
    private final MutableLiveData<Resource<User>> authResult = new MutableLiveData<>();
    private final MutableLiveData<Resource<User>> currentUser = new MutableLiveData<>();

    public AuthViewModel() {
        authRepository = AuthRepository.getInstance();
    }

    // ==========================================================
    // EXPOSE LiveData (chỉ đọc từ View)
    // ==========================================================

    public MutableLiveData<Resource<User>> getAuthResult() {
        return authResult;
    }

    public MutableLiveData<Resource<User>> getCurrentUser() {
        return currentUser;
    }

    // ==========================================================
    // ACTIONS (View gọi các method này)
    // ==========================================================

    /**
     * Xử lý đăng nhập.
     * View chỉ cần gọi loginWithEmail() và observe authResult.
     */
    public void loginWithEmail(String email, String password) {
        authRepository.login(email, password, authResult);
    }

    /**
     * Xử lý đăng ký tài khoản mới.
     */
    public void register(String email, String password,
                         String displayName, String phone, String role) {
        if (com.example.footballbooking.utils.Constants.ROLE_OWNER.equals(role)) {
            authRepository.registerOwner(email, password, displayName, phone, authResult);
        } else {
            authRepository.register(email, password, displayName, phone, authResult);
        }
    }

    /**
     * Kiểm tra session — gọi trong SplashActivity.
     * Nếu đã đăng nhập → currentUser.data != null.
     * Nếu chưa đăng nhập → currentUser.data == null (nhưng status = SUCCESS).
     */
    public void checkLoginSession() {
        authRepository.checkCurrentUser(currentUser);
    }

    /**
     * Đăng xuất.
     */
    public void logout() {
        authRepository.logout();
    }

    // ==========================================================
    // VALIDATION HELPERS (xử lý ở ViewModel, không ở View)
    // ==========================================================

    public String validateLoginInput(String email, String password) {
        if (email == null || email.trim().isEmpty())
            return "Vui lòng nhập email";
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches())
            return "Email không đúng định dạng";
        if (password == null || password.isEmpty())
            return "Vui lòng nhập mật khẩu";
        if (password.length() < 6)
            return "Mật khẩu phải có ít nhất 6 ký tự";
        return null; // null = hợp lệ
    }

    public String validateRegisterInput(String email, String password,
                                        String confirmPassword,
                                        String displayName, String phone) {
        String loginValidation = validateLoginInput(email, password);
        if (loginValidation != null) return loginValidation;

        if (!password.equals(confirmPassword))
            return "Xác nhận mật khẩu không khớp";
        if (displayName == null || displayName.trim().isEmpty())
            return "Vui lòng nhập họ tên";
        if (phone == null || phone.trim().isEmpty())
            return "Vui lòng nhập số điện thoại";
        if (!phone.matches("^(0|\\+84)[0-9]{8,9}$"))
            return "Số điện thoại không hợp lệ";
        return null;
    }
}
