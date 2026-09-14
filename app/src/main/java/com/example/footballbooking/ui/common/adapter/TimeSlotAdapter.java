package com.example.footballbooking.ui.common.adapter;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.footballbooking.R;
import com.example.footballbooking.data.model.TimeSlot;
import com.example.footballbooking.databinding.ItemTimeSlotBinding;

/**
 * TimeSlotAdapter — Grid adapter cho lưới chọn khung giờ.
 * Hiển thị màu khác nhau theo trạng thái: available (xanh), booked (xám), selected (đậm xanh).
 */
public class TimeSlotAdapter extends ListAdapter<TimeSlot, TimeSlotAdapter.SlotViewHolder> {

    public interface OnSlotClickListener {
        void onSlotClick(TimeSlot slot);
    }

    private final OnSlotClickListener listener;
    private String selectedSlotId = null; // ID slot đang được chọn

    private static final DiffUtil.ItemCallback<TimeSlot> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<TimeSlot>() {
                @Override
                public boolean areItemsTheSame(@NonNull TimeSlot o, @NonNull TimeSlot n) {
                    return o.getSlotId() != null && o.getSlotId().equals(n.getSlotId());
                }
                @Override
                public boolean areContentsTheSame(@NonNull TimeSlot o, @NonNull TimeSlot n) {
                    return o.isActive() == n.isActive() && o.isPeakHour() == n.isPeakHour();
                }
            };

    public TimeSlotAdapter(OnSlotClickListener listener) {
        super(DIFF_CALLBACK);
        this.listener = listener;
    }

    public void setSelectedSlotId(String slotId) {
        this.selectedSlotId = slotId;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public SlotViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemTimeSlotBinding binding = ItemTimeSlotBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new SlotViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull SlotViewHolder holder, int position) {
        holder.bind(getItem(position), listener, selectedSlotId);
    }

    static class SlotViewHolder extends RecyclerView.ViewHolder {
        private final ItemTimeSlotBinding b;

        SlotViewHolder(ItemTimeSlotBinding binding) {
            super(binding.getRoot());
            this.b = binding;
        }

        void bind(TimeSlot slot, OnSlotClickListener listener, String selectedSlotId) {
            b.tvSlotTime.setText(slot.getStartTime() + "\n" + slot.getEndTime());
            b.tvPeakBadge.setVisibility(slot.isPeakHour() ? View.VISIBLE : View.GONE);

            boolean isSelected = slot.getSlotId() != null
                    && slot.getSlotId().equals(selectedSlotId);

            // Màu card theo trạng thái
            if (isSelected) {
                // Đang chọn → xanh đậm
                b.cardTimeSlot.setStrokeColor(
                        ContextCompat.getColor(b.getRoot().getContext(), R.color.primary_light));
                b.cardTimeSlot.setStrokeWidth(3);
                b.cardTimeSlot.setCardBackgroundColor(
                        ContextCompat.getColor(b.getRoot().getContext(), R.color.primary_variant));
                b.tvSlotTime.setTextColor(
                        ContextCompat.getColor(b.getRoot().getContext(), R.color.white));
                b.tvSlotStatus.setText("✓ Đã chọn");
                b.tvSlotStatus.setTextColor(
                        ContextCompat.getColor(b.getRoot().getContext(), R.color.primary_light));
            } else {
                // Bình thường
                b.cardTimeSlot.setStrokeWidth(1);
                b.cardTimeSlot.setStrokeColor(
                        ContextCompat.getColor(b.getRoot().getContext(), R.color.divider));
                b.cardTimeSlot.setCardBackgroundColor(
                        ContextCompat.getColor(b.getRoot().getContext(), R.color.background_card));
                b.tvSlotTime.setTextColor(
                        ContextCompat.getColor(b.getRoot().getContext(), R.color.text_primary));
                b.tvSlotStatus.setText("Còn trống");
                b.tvSlotStatus.setTextColor(
                        ContextCompat.getColor(b.getRoot().getContext(), R.color.status_approved));
            }

            b.cardTimeSlot.setOnClickListener(v -> {
                if (listener != null) listener.onSlotClick(slot);
            });
        }
    }
}
