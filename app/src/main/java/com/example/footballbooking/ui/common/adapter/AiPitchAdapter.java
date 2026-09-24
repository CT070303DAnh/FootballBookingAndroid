package com.example.footballbooking.ui.common.adapter;

import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.example.footballbooking.R;
import com.example.footballbooking.databinding.ItemAiPitchCardBinding;
import com.example.footballbooking.ui.customer.pitch.PitchDetailActivity;
import com.example.footballbooking.utils.AiScoringEngine;
import com.example.footballbooking.utils.Constants;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * AiPitchAdapter — Hiển thị danh sách sân được AI gợi ý.
 *
 * ĐẶC BIỆT so với PitchAdapter bình thường:
 * - Hiển thị badge ranking (#1, #2, #3...)
 * - Score circle với phần trăm
 * - 3 progress bar mini cho từng thành phần điểm
 * - Text lý giải tại sao AI gợi ý sân này
 */
public class AiPitchAdapter
        extends ListAdapter<AiScoringEngine.ScoredPitch, AiPitchAdapter.AiViewHolder> {

    public interface OnAiPitchClickListener {
        void onClick(AiScoringEngine.ScoredPitch scoredPitch);
    }

    private final OnAiPitchClickListener listener;
    private final NumberFormat currencyFmt =
            NumberFormat.getNumberInstance(new Locale("vi", "VN"));

    private static final DiffUtil.ItemCallback<AiScoringEngine.ScoredPitch> DIFF =
            new DiffUtil.ItemCallback<AiScoringEngine.ScoredPitch>() {
                @Override
                public boolean areItemsTheSame(@NonNull AiScoringEngine.ScoredPitch o,
                                               @NonNull AiScoringEngine.ScoredPitch n) {
                    return o.pitch.getPitchId() != null
                            && o.pitch.getPitchId().equals(n.pitch.getPitchId());
                }
                @Override
                public boolean areContentsTheSame(@NonNull AiScoringEngine.ScoredPitch o,
                                                  @NonNull AiScoringEngine.ScoredPitch n) {
                    return o.totalScore == n.totalScore;
                }
            };

    public AiPitchAdapter(OnAiPitchClickListener listener) {
        super(DIFF);
        this.listener = listener;
    }

    @NonNull
    @Override
    public AiViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemAiPitchCardBinding binding = ItemAiPitchCardBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new AiViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull AiViewHolder holder, int position) {
        holder.bind(getItem(position), position + 1, listener, currencyFmt);
    }

    static class AiViewHolder extends RecyclerView.ViewHolder {
        private final ItemAiPitchCardBinding b;

        AiViewHolder(ItemAiPitchCardBinding binding) {
            super(binding.getRoot());
            this.b = binding;
        }

        void bind(AiScoringEngine.ScoredPitch sp, int rank,
                  OnAiPitchClickListener listener, NumberFormat fmt) {

            // --- Rank badge ---
            b.tvAiRank.setText("#" + rank);
            // Màu badge theo rank: vàng #1, bạc #2, đồng #3, xám còn lại
            if (rank == 1)      b.tvAiRank.setBackgroundResource(R.drawable.bg_rank_badge);
            else if (rank == 2) b.tvAiRank.setBackgroundColor(0xFF9E9E9E);
            else if (rank == 3) b.tvAiRank.setBackgroundColor(0xFFCD7F32);
            else                b.tvAiRank.setBackgroundColor(0xFF424242);

            // --- Score circle ---
            b.tvAiScore.setText(sp.getScorePercent() + "%");

            // --- Tên và lý do ---
            b.tvAiPitchName.setText(sp.pitch.getName());
            b.tvAiReason.setText(sp.recommendation);

            // --- Giá ---
            b.tvAiPrice.setText(fmt.format((long) sp.pitch.getBasePrice()) + "đ/h");

            // --- Progress bars (0-100) ---
            b.progressDistance.setProgress(Math.round(sp.distanceScore * 100));
            b.progressWeather.setProgress(Math.round(sp.weatherScore * 100));
            b.progressRating.setProgress(Math.round(sp.ratingScore * 100));

            // --- Ảnh ---
            String img = (sp.pitch.getImageUrls() != null && !sp.pitch.getImageUrls().isEmpty())
                    ? sp.pitch.getImageUrls().get(0) : null;
            Glide.with(b.getRoot().getContext())
                    .load(img)
                    .placeholder(R.drawable.bg_home_header)
                    .error(R.drawable.bg_auth_header)
                    .transition(DrawableTransitionOptions.withCrossFade(300))
                    .centerCrop()
                    .into(b.ivAiPitchImage);

            // --- Click ---
            b.getRoot().setOnClickListener(v -> {
                if (listener != null) listener.onClick(sp);
            });
            b.btnAiBook.setOnClickListener(v -> {
                // Mở PitchDetailActivity
                Intent intent = new Intent(b.getRoot().getContext(), PitchDetailActivity.class);
                intent.putExtra(Constants.EXTRA_PITCH_ID, sp.pitch.getPitchId());
                b.getRoot().getContext().startActivity(intent);
            });
        }
    }
}
