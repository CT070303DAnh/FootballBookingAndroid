package com.example.footballbooking.ui.owner.booking;

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
import com.example.footballbooking.ui.common.adapter.OwnerBookingAdapter;
import com.example.footballbooking.ui.owner.OwnerViewModel;
import com.example.footballbooking.utils.Constants;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * OwnerBookingFragment — Duyệt đơn đặt sân cho Chủ sân.
 * Tái sử dụng layout fragment_admin_booking.xml và AdminBookingAdapter.
 * Chỉ hiển thị đơn thuộc sân của Owner (filter bởi OwnerRepository).
 */
public class OwnerBookingFragment extends Fragment
        implements OwnerBookingAdapter.OnOwnerBookingActionListener {

    private FragmentAdminBookingBinding binding;
    private OwnerViewModel ownerViewModel;
    private OwnerBookingAdapter adapter;

    private List<Booking> allBookings = new ArrayList<>();
    private int currentTab = 0;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        // Tái sử dụng layout Admin Booking
        binding = FragmentAdminBookingBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ownerViewModel = new ViewModelProvider(requireActivity()).get(OwnerViewModel.class);

        adapter = new OwnerBookingAdapter(this);
        binding.rvAdminBookings.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvAdminBookings.setAdapter(adapter);

        binding.tabLayoutBooking.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                currentTab = tab.getPosition();
                filterAndUpdate();
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });

        observeViewModel();
    }

    private void observeViewModel() {
        ownerViewModel.getMyBookings().observe(getViewLifecycleOwner(), resource -> {
            if (resource == null) return;
            binding.progressAdminBooking.setVisibility(
                    resource.isLoading() ? View.VISIBLE : View.GONE);
            if (resource.isSuccess() && resource.data != null) {
                allBookings = resource.data;
                filterAndUpdate();
            } else if (resource.isError()) {
                Snackbar.make(binding.getRoot(), resource.message, Snackbar.LENGTH_LONG).show();
            }
        });

        ownerViewModel.getActionResult().observe(getViewLifecycleOwner(), resource -> {
            if (resource != null && resource.isSuccess())
                Snackbar.make(binding.getRoot(), "✅ Thao tác thành công", Snackbar.LENGTH_SHORT).show();
            else if (resource != null && resource.isError())
                Snackbar.make(binding.getRoot(), "Lỗi: " + resource.message, Snackbar.LENGTH_LONG).show();
        });
    }

    private void filterAndUpdate() {
        List<Booking> filtered;
        switch (currentTab) {
            case 1:
                filtered = allBookings.stream()
                        .filter(b -> Constants.STATUS_PENDING.equals(b.getBookingStatus()))
                        .collect(Collectors.toList());
                break;
            case 2:
                filtered = allBookings.stream()
                        .filter(b -> Constants.STATUS_APPROVED.equals(b.getBookingStatus()))
                        .collect(Collectors.toList());
                break;
            case 3:
                filtered = allBookings.stream()
                        .filter(b -> Constants.STATUS_REJECTED.equals(b.getBookingStatus())
                                || Constants.STATUS_CANCELLED.equals(b.getBookingStatus()))
                        .collect(Collectors.toList());
                break;
            default:
                filtered = allBookings;
        }

        boolean isEmpty = filtered.isEmpty();
        binding.rvAdminBookings.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        binding.layoutAdminBookingEmpty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        if (!isEmpty) adapter.submitList(new ArrayList<>(filtered));
    }

    @Override
    public void onApprove(Booking booking) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Duyệt đơn?")
                .setMessage("Xác nhận duyệt đơn của " + booking.getCustomerName()
                        + "\n🏟️ " + booking.getPitchName()
                        + "\n⏰ " + booking.getStartTime() + " - " + booking.getBookingDate())
                .setPositiveButton("✓ Duyệt", (d, w) -> ownerViewModel.approveBooking(booking))
                .setNegativeButton("Hủy", null)
                .show();
    }

    @Override
    public void onReject(Booking booking) {
        EditText etReason = new EditText(requireContext());
        etReason.setHint("Lý do từ chối");
        etReason.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        LinearLayout container = new LinearLayout(requireContext());
        container.setPadding(48, 24, 48, 0);
        container.addView(etReason);

        new AlertDialog.Builder(requireContext())
                .setTitle("Từ chối đơn đặt sân")
                .setView(container)
                .setPositiveButton("Từ chối", (d, w) -> {
                    String reason = etReason.getText().toString().trim();
                    if (reason.isEmpty()) {
                        Snackbar.make(binding.getRoot(), "Nhập lý do từ chối", Snackbar.LENGTH_SHORT).show();
                        return;
                    }
                    ownerViewModel.rejectBooking(booking, reason);
                })
                .setNegativeButton("Hủy", null).show();
    }

    @Override
    public void onMatchStatusChange(String bookingId, String newStatus) {
        ownerViewModel.updateMatchStatus(bookingId, newStatus);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
