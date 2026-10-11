package com.example.footballbooking.ui.customer.availability;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.footballbooking.R;
import com.example.footballbooking.data.model.Pitch;
import com.example.footballbooking.data.model.TimeSlot;
import com.example.footballbooking.data.repository.BookingRepository;
import com.example.footballbooking.databinding.FragmentAvailabilityBinding;
import com.example.footballbooking.ui.common.adapter.PitchAdapter;
import com.example.footballbooking.ui.common.adapter.TimeSlotAdapter;
import com.example.footballbooking.ui.customer.booking.BookingActivity;
import com.example.footballbooking.utils.Constants;
import com.example.footballbooking.utils.Resource;
import com.google.android.material.snackbar.Snackbar;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class AvailabilityFragment extends Fragment implements TimeSlotAdapter.OnSlotClickListener {

    private FragmentAvailabilityBinding binding;
    private AvailabilityViewModel viewModel;
    private TimeSlotAdapter slotAdapter;
    private PitchAdapter pitchAdapter;
    private String selectedDate;
    private TimeSlot selectedSlot;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentAvailabilityBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(AvailabilityViewModel.class);
        selectedDate = today();

        slotAdapter = new TimeSlotAdapter(this);
        binding.rvAvailabilitySlots.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        binding.rvAvailabilitySlots.setAdapter(slotAdapter);

        pitchAdapter = new PitchAdapter(this::openBooking);
        binding.rvAvailablePitches.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvAvailablePitches.setAdapter(pitchAdapter);
        binding.rvAvailablePitches.setNestedScrollingEnabled(false);

        binding.calendarAvailability.setMinDate(System.currentTimeMillis() - 1000);
        binding.calendarAvailability.setOnDateChangeListener((calendarView, year, month, day) -> {
            Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
            calendar.clear();
            calendar.set(year, month, day);
            selectedDate = formatDate(calendar.getTime());
            selectedSlot = null;
            showSlotsForDate();
            showHint();
        });

        viewModel.getAvailablePitches().observe(getViewLifecycleOwner(), this::renderResult);
        showSlotsForDate();
        showHint();
    }

    private void showSlotsForDate() {
        List<TimeSlot> slots = new ArrayList<>();
        for (TimeSlot slot : BookingRepository.getDefaultTimeSlots()) {
            if (isPast(slot.getStartTime())) {
                slot.setStatus("past");
                slot.setAvailable(false);
            }
            slots.add(slot);
        }
        slotAdapter.setSelectedSlotId(null);
        slotAdapter.submitList(slots);
        binding.tvAvailabilityDate.setText("Ngày " + selectedDate);
    }

    private void renderResult(Resource<List<Pitch>> resource) {
        if (resource == null) return;
        if (resource.isLoading()) {
            binding.progressAvailability.setVisibility(View.VISIBLE);
            binding.layoutAvailabilityEmpty.setVisibility(View.GONE);
            return;
        }

        binding.progressAvailability.setVisibility(View.GONE);
        if (resource.isSuccess()) {
            List<Pitch> pitches = resource.data == null ? new ArrayList<>() : resource.data;
            pitchAdapter.submitList(pitches);
            binding.tvAvailabilityCount.setText(pitches.size() + " sân còn trống");
            binding.layoutAvailabilityEmpty.setVisibility(pitches.isEmpty() ? View.VISIBLE : View.GONE);
            binding.tvAvailabilityEmpty.setText(pitches.isEmpty()
                    ? "Không có sân trống trong khung giờ này."
                    : "");
        } else if (resource.isError()) {
            pitchAdapter.submitList(new ArrayList<>());
            binding.layoutAvailabilityEmpty.setVisibility(View.VISIBLE);
            binding.tvAvailabilityEmpty.setText(resource.message);
            Snackbar.make(binding.getRoot(), resource.message, Snackbar.LENGTH_LONG).show();
        }
    }

    private void showHint() {
        selectedSlot = null;
        binding.tvAvailabilityCount.setText("Chọn một khung giờ để xem sân còn trống");
        pitchAdapter.submitList(new ArrayList<>());
        binding.layoutAvailabilityEmpty.setVisibility(View.VISIBLE);
        binding.tvAvailabilityEmpty.setText("Chưa chọn khung giờ");
        binding.progressAvailability.setVisibility(View.GONE);
    }

    @Override
    public void onSlotClick(TimeSlot slot) {
        if (slot == null || !slot.isAvailable()) {
            Snackbar.make(binding.getRoot(), "Khung giờ này đã qua hoặc không khả dụng.", Snackbar.LENGTH_SHORT).show();
            return;
        }
        selectedSlot = slot;
        slotAdapter.setSelectedSlotId(slot.getSlotId());
        binding.tvAvailabilityCount.setText("Đang tìm sân cho " + slot.getDisplayLabel());
        viewModel.load(selectedDate, slot);
    }

    private void openBooking(Pitch pitch) {
        Intent intent = new Intent(requireContext(), BookingActivity.class);
        intent.putExtra(Constants.EXTRA_PITCH_ID, pitch.getPitchId());
        intent.putExtra(Constants.EXTRA_BOOKING_DATE, selectedDate);
        intent.putExtra(Constants.EXTRA_SLOT_ID, selectedSlot == null ? null : selectedSlot.getSlotId());
        startActivity(intent);
    }

    private boolean isPast(String startTime) {
        if (!selectedDate.equals(today())) return selectedDate.compareTo(today()) < 0;
        return startTime != null && startTime.compareTo(currentTime()) <= 0;
    }

    private String today() {
        return formatDate(new Date());
    }

    private String currentTime() {
        SimpleDateFormat format = new SimpleDateFormat("HH:mm", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
        return format.format(new Date());
    }

    private String formatDate(Date date) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
        return format.format(date);
    }

    @Override
    public void onDestroyView() {
        binding.rvAvailabilitySlots.setAdapter(null);
        binding.rvAvailablePitches.setAdapter(null);
        super.onDestroyView();
        binding = null;
    }
}
