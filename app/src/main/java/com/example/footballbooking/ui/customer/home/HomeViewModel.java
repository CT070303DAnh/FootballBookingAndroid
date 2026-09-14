package com.example.footballbooking.ui.customer.home;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.footballbooking.data.model.User;
import com.example.footballbooking.data.repository.AuthRepository;
import com.example.footballbooking.utils.Resource;

import java.util.Calendar;

/**
 * HomeViewModel — Logic riêng của HomeFragment.
 * Quản lý thông tin user hiển thị và lời chào động.
 *
 * PitchViewModel được dùng ở scope Activity (share toàn app),
 * HomeViewModel chỉ phục vụ riêng HomeFragment.
 */
public class HomeViewModel extends ViewModel {

    private final AuthRepository authRepository;
    private final MutableLiveData<Resource<User>> currentUser = new MutableLiveData<>();

    public HomeViewModel() {
        authRepository = AuthRepository.getInstance();
        loadCurrentUser();
    }

    public MutableLiveData<Resource<User>> getCurrentUser() {
        return currentUser;
    }

    private void loadCurrentUser() {
        authRepository.checkCurrentUser(currentUser);
    }

    /**
     * Tạo lời chào động theo giờ trong ngày.
     * Gọi từ Fragment để hiển thị "Chào buổi sáng/chiều/tối".
     */
    public String getGreeting() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour >= 5 && hour < 12)  return "Chào buổi sáng! ☀️";
        if (hour >= 12 && hour < 18) return "Chào buổi chiều! 🌤";
        return "Chào buổi tối! 🌙";
    }

    public void logout() {
        authRepository.logout();
    }
}
