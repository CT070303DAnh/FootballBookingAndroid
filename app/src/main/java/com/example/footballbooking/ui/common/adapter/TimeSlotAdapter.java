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
                    return o.isActive() == n.isActive()
                            && o.isPeakHour() == n.isPeakHour()
                            && o.isAvailable() == n.isAvailable()
                            && (o.getStatus() != null && o.getStatus().equals(n.getStatus()));
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
            android.content.Context ctx = b.getRoot().getContext();
            b.tvSlotTime.setText(slot.getStartTime() + " - " + slot.getEndTime());
            b.tvPeakBadge.setVisibility(slot.isPeakHour() ? View.VISIBLE : View.GONE);

            boolean isAvailable = slot.isAvailable();
            boolean isSelected = isAvailable && slot.getSlotId() != null
                    && slot.getSlotId().equals(selectedSlotId);

            if (!isAvailable) {
                // Khung giờ đã được đặt hoặc đã qua
                b.cardTimeSlot.setAlpha(0.55f);
                b.cardTimeSlot.setStrokeWidth(1);
                b.cardTimeSlot.setStrokeColor(ContextCompat.getColor(ctx, R.color.divider));
                b.cardTimeSlot.setCardBackgroundColor(ContextCompat.getColor(ctx, R.color.background_dark));
                b.tvSlotTime.setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary));

                if ("past".equalsIgnoreCase(slot.getStatus())) {
                    b.tvSlotStatus.setText("Đã qua");
                    b.tvSlotStatus.setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary));
                } else {
                    b.tvSlotStatus.setText("🔒 Đã đặt");
                    b.tvSlotStatus.setTextColor(ContextCompat.getColor(ctx, R.color.status_rejected));
                }

                b.cardTimeSlot.setOnClickListener(v ->
                        android.widget.Toast.makeText(ctx,
                                "Khung giờ này không khả dụng!",
                                android.widget.Toast.LENGTH_SHORT).show()
                );
            } else {
                // Khung giờ còn trống
                b.cardTimeSlot.setAlpha(1.0f);

                if (isSelected) {
                    // Đang chọn → xanh đậm
                    b.cardTimeSlot.setStrokeColor(
                            ContextCompat.getColor(ctx, R.color.primary_light));
                    b.cardTimeSlot.setStrokeWidth(3);
                    b.cardTimeSlot.setCardBackgroundColor(
                            ContextCompat.getColor(ctx, R.color.primary_variant));
                    b.tvSlotTime.setTextColor(
                            ContextCompat.getColor(ctx, R.color.white));
                    b.tvSlotStatus.setText("✓ Đã chọn");
                    b.tvSlotStatus.setTextColor(
                            ContextCompat.getColor(ctx, R.color.primary_light));
                } else {
                    // Bình thường
                    b.cardTimeSlot.setStrokeWidth(1);
                    b.cardTimeSlot.setStrokeColor(
                            ContextCompat.getColor(ctx, R.color.divider));
                    b.cardTimeSlot.setCardBackgroundColor(
                            ContextCompat.getColor(ctx, R.color.background_card));
                    b.tvSlotTime.setTextColor(
                            ContextCompat.getColor(ctx, R.color.text_primary));
                    b.tvSlotStatus.setText("Còn trống");
                    b.tvSlotStatus.setTextColor(
                            ContextCompat.getColor(ctx, R.color.status_approved));
                }

                b.cardTimeSlot.setOnClickListener(v -> {
                    if (listener != null) listener.onSlotClick(slot);
                });
            }
        }
    }
}
