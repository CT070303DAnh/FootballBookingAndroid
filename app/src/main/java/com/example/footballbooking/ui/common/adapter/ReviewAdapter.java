package com.example.footballbooking.ui.common.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.footballbooking.R;
import com.example.footballbooking.data.model.Review;
import com.example.footballbooking.databinding.ItemReviewCardBinding;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ReviewAdapter extends ListAdapter<Review, ReviewAdapter.ReviewViewHolder> {

    private static final DiffUtil.ItemCallback<Review> DIFF =
            new DiffUtil.ItemCallback<Review>() {
                @Override
                public boolean areItemsTheSame(@NonNull Review oldItem, @NonNull Review newItem) {
                    return oldItem.getReviewId() != null
                            && oldItem.getReviewId().equals(newItem.getReviewId());
                }

                @Override
                public boolean areContentsTheSame(@NonNull Review oldItem, @NonNull Review newItem) {
                    return oldItem.getRating() == newItem.getRating()
                            && java.util.Objects.equals(oldItem.getComment(), newItem.getComment())
                            && java.util.Objects.equals(oldItem.getCreatedAt(), newItem.getCreatedAt());
                }
            };

    public ReviewAdapter() {
        super(DIFF);
    }

    @NonNull
    @Override
    public ReviewViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ReviewViewHolder(ItemReviewCardBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ReviewViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    static class ReviewViewHolder extends RecyclerView.ViewHolder {
        private final ItemReviewCardBinding binding;

        ReviewViewHolder(ItemReviewCardBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Review review) {
            binding.tvReviewerName.setText(
                    review.getCustomerName() == null || review.getCustomerName().trim().isEmpty()
                            ? "Người dùng" : review.getCustomerName());
            binding.ratingReview.setRating(review.getRating());
            binding.tvReviewComment.setText(
                    review.getComment() == null || review.getComment().trim().isEmpty()
                            ? "Không có nhận xét" : review.getComment());

            if (review.getCreatedAt() != null) {
                Date date = review.getCreatedAt().toDate();
                binding.tvReviewDate.setText(new SimpleDateFormat(
                        "dd/MM/yyyy", Locale.getDefault()).format(date));
            } else {
                binding.tvReviewDate.setText("");
            }

            Glide.with(binding.getRoot().getContext())
                    .load(review.getCustomerAvatar())
                    .placeholder(android.R.drawable.ic_menu_myplaces)
                    .error(android.R.drawable.ic_menu_myplaces)
                    .circleCrop()
                    .into(binding.civReviewerAvatar);
        }
    }
}
