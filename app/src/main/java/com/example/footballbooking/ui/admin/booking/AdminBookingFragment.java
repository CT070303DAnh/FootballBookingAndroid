package com.example.footballbooking.ui.admin.booking;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.footballbooking.data.model.Booking;
import com.example.footballbooking.databinding.FragmentAdminBookingBinding;
import com.example.footballbooking.ui.admin.AdminViewModel;
import com.example.footballbooking.ui.common.adapter.AdminBookingAdapter;
import com.example.footballbooking.utils.Constants;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * AdminBookingFragment — Giám sát đơn đặt sân (Chỉ xem).
 */
public class AdminBookingFragment extends Fragment {

    private FragmentAdminBookingBinding binding;
    private AdminViewModel adminViewModel;
    private AdminBookingAdapter adminBookingAdapter;

    private List<Booking> allBookingsList = new ArrayList<>();
    private int currentTab = 0; // 0=All, 1=Pending, 2=Approved, 3=Rejected

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentAdminBookingBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        adminViewModel = new ViewModelProvider(requireActivity()).get(AdminViewModel.class);

        setupRecyclerView();
        setupTabs();
        observeViewModel();
        adminViewModel.loadAllBookings();
    }

    private void setupRecyclerView() {
        adminBookingAdapter = new AdminBookingAdapter();
        binding.rvAdminBookings.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvAdminBookings.setAdapter(adminBookingAdapter);
    }

    private void setupTabs() {
        binding.tabLayoutBooking.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                currentTab = tab.getPosition();
                filterAndUpdate();
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private void observeViewModel() {
        adminViewModel.getAllBookings().observe(getViewLifecycleOwner(), resource -> {
            if (resource == null) return;
            switch (resource.status) {
                case LOADING:
                    binding.progressAdminBooking.setVisibility(View.VISIBLE);
                    break;
                case SUCCESS:
                    binding.progressAdminBooking.setVisibility(View.GONE);
                    allBookingsList = resource.data != null ? resource.data : new ArrayList<>();
                    filterAndUpdate();
                    break;
                case ERROR:
                    binding.progressAdminBooking.setVisibility(View.GONE);
                    Snackbar.make(binding.getRoot(), resource.message, Snackbar.LENGTH_LONG).show();
                    break;
            }
        });
    }

    /** Lọc danh sách theo tab đang chọn */
    private void filterAndUpdate() {
        List<Booking> filtered;
        switch (currentTab) {
            case 1:
                filtered = allBookingsList.stream()
                        .filter(b -> Constants.STATUS_PENDING.equals(b.getBookingStatus()))
                        .collect(Collectors.toList());
                break;
            case 2:
                filtered = allBookingsList.stream()
                        .filter(b -> Constants.STATUS_APPROVED.equals(b.getBookingStatus()))
                        .collect(Collectors.toList());
                break;
            case 3:
                filtered = allBookingsList.stream()
                        .filter(b -> Constants.STATUS_REJECTED.equals(b.getBookingStatus())
                                  || Constants.STATUS_CANCELLED.equals(b.getBookingStatus()))
                        .collect(Collectors.toList());
                break;
            default:
                filtered = allBookingsList;
        }

        boolean isEmpty = filtered.isEmpty();
        binding.rvAdminBookings.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        binding.layoutAdminBookingEmpty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);

        if (!isEmpty) adminBookingAdapter.submitList(new ArrayList<>(filtered));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
