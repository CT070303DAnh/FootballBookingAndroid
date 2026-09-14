package com.example.footballbooking.ui.admin.booking;

import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
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
 * AdminBookingFragment — Quản lý đơn đặt sân (Duyệt / Từ chối / Realtime).
 *
 * TÍNH NĂNG:
 * - Tabs: Tất cả | Chờ duyệt | Đã duyệt | Từ chối
 * - Realtime listener — khi customer đặt đơn mới, Admin thấy ngay
 * - Duyệt đơn → 1 tap
 * - Từ chối → Dialog nhập lý do
 * - Cập nhật Match Status realtime (Sắp đá → Đang đá → Kết thúc)
 */
public class AdminBookingFragment extends Fragment
        implements AdminBookingAdapter.OnAdminBookingActionListener {

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
        adminBookingAdapter = new AdminBookingAdapter(this);
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

        adminViewModel.getActionResult().observe(getViewLifecycleOwner(), resource -> {
            if (resource == null) return;
            if (resource.isSuccess()) {
                Snackbar.make(binding.getRoot(), "Thao tác thành công ✅", Snackbar.LENGTH_SHORT).show();
            } else if (resource.isError()) {
                Snackbar.make(binding.getRoot(), "Lỗi: " + resource.message, Snackbar.LENGTH_LONG).show();
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

    // ============================================================
    // ADAPTER CALLBACKS
    // ============================================================

    @Override
    public void onApprove(Booking booking) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Duyệt đơn đặt sân")
                .setMessage("Duyệt đơn của " + booking.getCustomerName()
                        + "\n🏟️ " + booking.getPitchName()
                        + "\n⏰ " + booking.getStartTime() + " ngày " + booking.getBookingDate())
                .setPositiveButton("✓ Duyệt", (d, w) ->
                        adminViewModel.approveBooking(booking))
                .setNegativeButton("Hủy", null)
                .show();
    }

    @Override
    public void onReject(Booking booking) {
        // Dialog với EditText để nhập lý do từ chối
        EditText etReason = new EditText(requireContext());
        etReason.setHint("Lý do từ chối (bắt buộc)");
        etReason.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        etReason.setMinLines(2);

        LinearLayout container = new LinearLayout(requireContext());
        container.setPadding(48, 24, 48, 0);
        container.addView(etReason);

        new AlertDialog.Builder(requireContext())
                .setTitle("Từ chối đơn đặt sân")
                .setMessage("Đơn của " + booking.getCustomerName())
                .setView(container)
                .setPositiveButton("Từ chối", (d, w) -> {
                    String reason = etReason.getText().toString().trim();
                    if (reason.isEmpty()) {
                        Snackbar.make(binding.getRoot(),
                                "Vui lòng nhập lý do từ chối", Snackbar.LENGTH_SHORT).show();
                        return;
                    }
                    adminViewModel.rejectBooking(booking, reason);
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    @Override
    public void onMatchStatusChange(String bookingId, String newStatus) {
        adminViewModel.updateMatchStatus(bookingId, newStatus);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
