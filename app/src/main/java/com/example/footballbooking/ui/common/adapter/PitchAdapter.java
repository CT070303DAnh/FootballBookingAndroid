package com.example.footballbooking.ui.common.adapter;

import android.content.Context;
import android.location.Location;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.example.footballbooking.R;
import com.example.footballbooking.data.model.Pitch;
import com.example.footballbooking.databinding.ItemPitchCardBinding;
import com.example.footballbooking.utils.Constants;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * PitchAdapter — RecyclerView Adapter dùng ListAdapter + DiffUtil.
 *
 * ƯU ĐIỂM của ListAdapter:
 * - DiffUtil tự động tính diff và animate thay đổi
 * - Không cần gọi notifyDataSetChanged() thủ công
 * - Hiệu năng tốt hơn với danh sách lớn
 */
public class PitchAdapter extends ListAdapter<Pitch, PitchAdapter.PitchViewHolder> {

    // Callback khi user click vào 1 sân
    public interface OnPitchClickListener {
        void onPitchClick(Pitch pitch);
    }

    private final OnPitchClickListener listener;
    private android.location.Location userLocation; // GPS user — để tính khoảng cách

    // DiffUtil callback — so sánh item theo pitchId
    private static final DiffUtil.ItemCallback<Pitch> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<Pitch>() {
                @Override
                public boolean areItemsTheSame(@NonNull Pitch oldItem, @NonNull Pitch newItem) {
                    // So sánh ID — 2 item là cùng 1 sân không?
                    return oldItem.getPitchId() != null &&
                           oldItem.getPitchId().equals(newItem.getPitchId());
                }

                @Override
                public boolean areContentsTheSame(@NonNull Pitch oldItem, @NonNull Pitch newItem) {
                    // So sánh nội dung — có thay đổi gì không?
                    return oldItem.getRating() == newItem.getRating() &&
                           oldItem.getStatus().equals(newItem.getStatus()) &&
                           oldItem.getBasePrice() == newItem.getBasePrice();
                }
            };

    public PitchAdapter(OnPitchClickListener listener) {
        super(DIFF_CALLBACK);
        this.listener = listener;
    }

    /** Cập nhật vị trí user để tính khoảng cách */
    public void setUserLocation(android.location.Location location) {
        this.userLocation = location;
        notifyDataSetChanged(); // Refresh để tính lại khoảng cách
    }

    @NonNull
    @Override
    public PitchViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemPitchCardBinding binding = ItemPitchCardBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new PitchViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull PitchViewHolder holder, int position) {
        holder.bind(getItem(position), listener, userLocation);
    }

    // ============================================================
    // VIEW HOLDER
    // ============================================================

    static class PitchViewHolder extends RecyclerView.ViewHolder {

        private final ItemPitchCardBinding binding;
        private final NumberFormat currencyFormatter =
                NumberFormat.getNumberInstance(new Locale("vi", "VN"));

        PitchViewHolder(ItemPitchCardBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Pitch pitch, OnPitchClickListener listener,
                  android.location.Location userLocation) {

            Context ctx = binding.getRoot().getContext();

            // --- Tên và địa chỉ ---
            binding.tvPitchName.setText(pitch.getName());
            binding.tvPitchAddress.setText("📍 " + pitch.getAddress());

            // --- Loại sân ---
            binding.chipPitchType.setText("Sân " + pitch.getType() + " người");

            // --- Trạng thái sân ---
            if (Constants.PITCH_AVAILABLE.equals(pitch.getStatus())) {
                binding.chipStatus.setText("Còn sân");
                binding.chipStatus.setChipBackgroundColorResource(R.color.status_approved);
            } else if (Constants.PITCH_CLOSED.equals(pitch.getStatus())) {
                binding.chipStatus.setText("Đã đóng");
                binding.chipStatus.setChipBackgroundColorResource(R.color.status_cancelled);
            }

            // --- Rating ---
            binding.ratingBar.setRating(pitch.getRating());
            binding.tvRating.setText(
                    String.format(Locale.getDefault(), "%.1f (%d)",
                            pitch.getRating(), pitch.getTotalReviews()));

            // --- Giá ---
            String priceText = currencyFormatter.format((long) pitch.getBasePrice()) + "đ/h";
            binding.tvPrice.setText(priceText);

            // --- Khoảng cách (nếu có GPS) ---
            if (userLocation != null && pitch.getLocation() != null) {
                float[] results = new float[1];
                Location.distanceBetween(
                        userLocation.getLatitude(), userLocation.getLongitude(),
                        pitch.getLocation().getLatitude(), pitch.getLocation().getLongitude(),
                        results);
                float distanceKm = results[0] / 1000f;
                if (distanceKm < 1f) {
                    binding.tvDistance.setText(
                            String.format(Locale.getDefault(), "📍 %.0f m", results[0]));
                } else {
                    binding.tvDistance.setText(
                            String.format(Locale.getDefault(), "📍 %.1f km", distanceKm));
                }
            } else {
                binding.tvDistance.setText("📍 --");
            }

            // --- Load ảnh với Glide ---
            String imageUrl = (pitch.getImageUrls() != null && !pitch.getImageUrls().isEmpty())
                    ? pitch.getImageUrls().get(0) : null;

            Glide.with(ctx)
                    .load(imageUrl)
                    .placeholder(R.drawable.bg_home_header) // placeholder khi chưa load
                    .error(R.drawable.bg_auth_header)        // fallback nếu lỗi
                    .transition(DrawableTransitionOptions.withCrossFade(300))
                    .centerCrop()
                    .into(binding.ivPitchImage);

            // --- Click handler ---
            binding.getRoot().setOnClickListener(v -> {
                if (listener != null) listener.onPitchClick(pitch);
            });
        }
    }
}
