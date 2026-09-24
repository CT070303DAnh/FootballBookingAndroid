package com.example.footballbooking.ui.common.adapter;

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
import com.example.footballbooking.databinding.ItemAdminPitchCardBinding;
import com.example.footballbooking.utils.Constants;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * AdminPitchAdapter — Hiển thị danh sách sân với nút Edit / Delete.
 */
public class AdminPitchAdapter
        extends ListAdapter<Pitch, AdminPitchAdapter.AdminPitchVH> {

    public interface OnAdminPitchActionListener {
        void onEdit(Pitch pitch);
        void onDelete(Pitch pitch);
    }

    private final OnAdminPitchActionListener listener;
    private final NumberFormat currencyFmt =
            NumberFormat.getNumberInstance(new Locale("vi", "VN"));

    private static final DiffUtil.ItemCallback<Pitch> DIFF =
            new DiffUtil.ItemCallback<Pitch>() {
                @Override
                public boolean areItemsTheSame(@NonNull Pitch o, @NonNull Pitch n) {
                    return o.getPitchId() != null && o.getPitchId().equals(n.getPitchId());
                }
                @Override
                public boolean areContentsTheSame(@NonNull Pitch o, @NonNull Pitch n) {
                    return o.getName().equals(n.getName())
                            && o.getStatus().equals(n.getStatus())
                            && o.getBasePrice() == n.getBasePrice();
                }
            };

    public AdminPitchAdapter(OnAdminPitchActionListener listener) {
        super(DIFF);
        this.listener = listener;
    }

    @NonNull @Override
    public AdminPitchVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new AdminPitchVH(
                ItemAdminPitchCardBinding.inflate(
                        LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull AdminPitchVH holder, int position) {
        holder.bind(getItem(position), listener, currencyFmt);
    }

    static class AdminPitchVH extends RecyclerView.ViewHolder {
        private final ItemAdminPitchCardBinding b;

        AdminPitchVH(ItemAdminPitchCardBinding binding) {
            super(binding.getRoot());
            this.b = binding;
        }

        void bind(Pitch pitch, OnAdminPitchActionListener listener,
                  NumberFormat fmt) {
            b.tvAdminPitchName.setText(pitch.getName());
            b.tvAdminPitchPrice.setText(fmt.format((long) pitch.getBasePrice()) + "đ/h");
            b.tvAdminPitchRating.setText("⭐ " + pitch.getRating()
                    + "  (" + pitch.getTotalReviews() + " đánh giá)");
            b.chipAdminPitchType.setText("Sân " + pitch.getType() + " người");

            // Status chip
            switch (pitch.getStatus() != null ? pitch.getStatus() : "") {
                case Constants.PITCH_AVAILABLE:
                    b.chipAdminPitchStatus.setText("Hoạt động");
                    b.chipAdminPitchStatus.setChipBackgroundColorResource(R.color.status_approved);
                    break;
                case Constants.PITCH_MAINTENANCE:
                    b.chipAdminPitchStatus.setText("Bảo trì");
                    b.chipAdminPitchStatus.setChipBackgroundColorResource(R.color.status_pending);
                    break;
                default:
                    b.chipAdminPitchStatus.setText("Đóng");
                    b.chipAdminPitchStatus.setChipBackgroundColorResource(R.color.status_cancelled);
                    break;
            }

            // Thumbnail
            String imgUrl = (pitch.getImageUrls() != null && !pitch.getImageUrls().isEmpty())
                    ? pitch.getImageUrls().get(0) : null;
            Glide.with(b.getRoot().getContext())
                    .load(imgUrl)
                    .placeholder(R.drawable.bg_home_header)
                    .error(R.drawable.bg_auth_header)
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .centerCrop()
                    .into(b.ivAdminPitchThumb);

            b.btnAdminEditPitch.setOnClickListener(v -> listener.onEdit(pitch));
            b.btnAdminDeletePitch.setOnClickListener(v -> listener.onDelete(pitch));
        }
    }
}
