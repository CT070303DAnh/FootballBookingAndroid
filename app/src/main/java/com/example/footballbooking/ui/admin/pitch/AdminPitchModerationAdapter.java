package com.example.footballbooking.ui.admin.pitch;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.footballbooking.R;
import com.example.footballbooking.data.model.Pitch;
import com.example.footballbooking.databinding.ItemAdminPitchModerationBinding;
import com.example.footballbooking.utils.Constants;

import java.text.NumberFormat;
import java.util.Locale;

public class AdminPitchModerationAdapter
        extends ListAdapter<Pitch, AdminPitchModerationAdapter.PitchViewHolder> {

    public static final String ACTION_APPROVE = "approve";
    public static final String ACTION_REJECT = "reject";
    public static final String ACTION_SUSPEND = "suspend";
    public static final String ACTION_ARCHIVE = "archive";
    public static final String ACTION_EDIT = "edit";

    public interface OnPitchActionListener {
        void onAction(Pitch pitch, String action);
    }

    private final OnPitchActionListener listener;
    private final NumberFormat money = NumberFormat.getNumberInstance(new Locale("vi", "VN"));

    private static final DiffUtil.ItemCallback<Pitch> DIFF = new DiffUtil.ItemCallback<Pitch>() {
        @Override
        public boolean areItemsTheSame(@NonNull Pitch oldItem, @NonNull Pitch newItem) {
            return oldItem.getPitchId() != null && oldItem.getPitchId().equals(newItem.getPitchId());
        }

        @Override
        public boolean areContentsTheSame(@NonNull Pitch oldItem, @NonNull Pitch newItem) {
            return safe(oldItem.getName()).equals(safe(newItem.getName()))
                    && safe(oldItem.getStatus()).equals(safe(newItem.getStatus()))
                    && safe(oldItem.getEffectiveModerationStatus())
                    .equals(safe(newItem.getEffectiveModerationStatus()))
                    && oldItem.getBasePrice() == newItem.getBasePrice()
                    && safe(oldItem.getModerationReason()).equals(safe(newItem.getModerationReason()));
        }

        private String safe(String value) { return value == null ? "" : value; }
    };

    public AdminPitchModerationAdapter(OnPitchActionListener listener) {
        super(DIFF);
        this.listener = listener;
    }

    @NonNull
    @Override
    public PitchViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new PitchViewHolder(ItemAdminPitchModerationBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull PitchViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    class PitchViewHolder extends RecyclerView.ViewHolder {
        private final ItemAdminPitchModerationBinding binding;

        PitchViewHolder(ItemAdminPitchModerationBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Pitch pitch) {
            binding.tvPitchName.setText(safe(pitch.getName()));
            binding.tvOwnerName.setText("Chủ sân: " + safe(pitch.getOwnerName()));
            binding.tvPitchAddress.setText("📍 " + safe(pitch.getAddress()));
            binding.tvPitchMeta.setText("Sân " + safe(pitch.getType()) + " người • "
                    + money.format((long) pitch.getBasePrice()) + "đ/giờ");
            binding.tvOperationalStatus.setText("Trạng thái hoạt động: "
                    + operationalLabel(pitch.getStatus()));
            binding.tvOperationalStatus.setTextColor(operationalColor(pitch.getStatus()));

            String imageUrl = pitch.getImageUrls() != null && !pitch.getImageUrls().isEmpty()
                    ? pitch.getImageUrls().get(0) : null;
            Glide.with(binding.getRoot().getContext())
                    .load(imageUrl)
                    .placeholder(R.drawable.ic_launcher_background)
                    .error(R.drawable.ic_launcher_background)
                    .centerCrop()
                    .into(binding.ivPitchImage);

            String moderation = pitch.getEffectiveModerationStatus();
            binding.tvPitchStatus.setText(moderationLabel(moderation));
            binding.tvPitchStatus.setTextColor(moderationColor(moderation));

            String reason = pitch.getModerationReason();
            if (reason != null && !reason.trim().isEmpty()
                    && !Constants.PITCH_MODERATION_APPROVED.equals(moderation)) {
                binding.tvModerationReason.setVisibility(View.VISIBLE);
                binding.tvModerationReason.setText("Lý do: " + reason);
            } else {
                binding.tvModerationReason.setVisibility(View.GONE);
            }

            boolean approved = Constants.PITCH_MODERATION_APPROVED.equals(moderation);
            boolean archived = Constants.PITCH_MODERATION_ARCHIVED.equals(moderation);
            boolean suspended = Constants.PITCH_MODERATION_SUSPENDED.equals(moderation);

            binding.btnApprove.setText(archived ? "Khôi phục" : "Duyệt");
            binding.btnApprove.setEnabled(!approved);
            binding.btnReject.setEnabled(!archived && !Constants.PITCH_MODERATION_REJECTED.equals(moderation));
            binding.btnSuspend.setText(suspended ? "Mở khóa" : "Khóa sân");
            binding.btnSuspend.setEnabled(!archived);
            binding.btnArchive.setText(archived ? "Đã ẩn" : "Ẩn sân");
            binding.btnArchive.setEnabled(!archived);

            binding.btnApprove.setOnClickListener(v -> listener.onAction(pitch, ACTION_APPROVE));
            binding.btnReject.setOnClickListener(v -> listener.onAction(pitch, ACTION_REJECT));
            binding.btnSuspend.setOnClickListener(v -> listener.onAction(
                    pitch, suspended ? ACTION_APPROVE : ACTION_SUSPEND));
            binding.btnArchive.setOnClickListener(v -> listener.onAction(pitch, ACTION_ARCHIVE));
            binding.btnEdit.setOnClickListener(v -> listener.onAction(pitch, ACTION_EDIT));
        }

        private String moderationLabel(String value) {
            if (Constants.PITCH_MODERATION_PENDING.equals(value)) return "Chờ duyệt";
            if (Constants.PITCH_MODERATION_APPROVED.equals(value)) return "Đã duyệt";
            if (Constants.PITCH_MODERATION_REJECTED.equals(value)) return "Từ chối";
            if (Constants.PITCH_MODERATION_SUSPENDED.equals(value)) return "Đang khóa";
            if (Constants.PITCH_MODERATION_ARCHIVED.equals(value)) return "Đã ẩn";
            return "Chưa phân loại";
        }

        private int moderationColor(String value) {
            if (Constants.PITCH_MODERATION_APPROVED.equals(value)) return Color.rgb(16, 185, 129);
            if (Constants.PITCH_MODERATION_REJECTED.equals(value)
                    || Constants.PITCH_MODERATION_SUSPENDED.equals(value)
                    || Constants.PITCH_MODERATION_ARCHIVED.equals(value)) {
                return Color.rgb(239, 68, 68);
            }
            return Color.rgb(245, 158, 11);
        }

        private String operationalLabel(String value) {
            if (Constants.PITCH_AVAILABLE.equals(value)) return "Đang hoạt động";
            if (Constants.PITCH_MAINTENANCE.equals(value)) return "Đang bảo trì";
            if (Constants.PITCH_CLOSED.equals(value)) return "Đã ẩn khỏi người dùng";
            if (Constants.PITCH_SUSPENDED.equals(value)) return "Đang bị khóa";
            return "Chưa xác định";
        }

        private int operationalColor(String value) {
            if (Constants.PITCH_AVAILABLE.equals(value)) return Color.rgb(16, 185, 129);
            if (Constants.PITCH_MAINTENANCE.equals(value)) return Color.rgb(245, 158, 11);
            return Color.rgb(239, 68, 68);
        }

        private String safe(String value) { return value == null ? "" : value; }
    }
}
