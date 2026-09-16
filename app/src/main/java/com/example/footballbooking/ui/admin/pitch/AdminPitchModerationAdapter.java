package com.example.footballbooking.ui.admin.pitch;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.footballbooking.R;
import com.example.footballbooking.data.model.Pitch;
import com.example.footballbooking.databinding.ItemAdminPitchModerationBinding;
import com.example.footballbooking.utils.Constants;

import java.util.ArrayList;
import java.util.List;

public class AdminPitchModerationAdapter extends RecyclerView.Adapter<AdminPitchModerationAdapter.PitchViewHolder> {

    private final Context context;
    private List<Pitch> pitchList = new ArrayList<>();
    private final OnPitchStatusChangeListener listener;

    public interface OnPitchStatusChangeListener {
        void onStatusChange(Pitch pitch, String newStatus);
    }

    public AdminPitchModerationAdapter(Context context, OnPitchStatusChangeListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void submitList(List<Pitch> newList) {
        this.pitchList = newList;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public PitchViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemAdminPitchModerationBinding binding = ItemAdminPitchModerationBinding.inflate(
                LayoutInflater.from(context), parent, false);
        return new PitchViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull PitchViewHolder holder, int position) {
        Pitch pitch = pitchList.get(position);
        holder.bind(pitch);
    }

    @Override
    public int getItemCount() {
        return pitchList != null ? pitchList.size() : 0;
    }

    class PitchViewHolder extends RecyclerView.ViewHolder {
        private final ItemAdminPitchModerationBinding binding;

        public PitchViewHolder(ItemAdminPitchModerationBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(Pitch pitch) {
            binding.tvPitchName.setText(pitch.getName());
            binding.tvOwnerName.setText("Chủ sân: " + (pitch.getOwnerName() != null ? pitch.getOwnerName() : ""));
            binding.tvPitchAddress.setText(pitch.getAddress());

            if (pitch.getImageUrls() != null && !pitch.getImageUrls().isEmpty()) {
                Glide.with(context)
                        .load(pitch.getImageUrls().get(0))
                        .placeholder(R.drawable.ic_launcher_background)
                        .centerCrop()
                        .into(binding.ivPitchImage);
            } else {
                binding.ivPitchImage.setImageResource(R.drawable.ic_launcher_background);
            }

            String status = pitch.getStatus();
            String displayStatus = status;
            int color = Color.GRAY;
            if (Constants.PITCH_AVAILABLE.equals(status)) {
                displayStatus = "Đang hoạt động";
                color = Color.parseColor("#4CAF50");
                binding.btnApprove.setEnabled(false);
                binding.btnSuspend.setEnabled(true);
            } else if (Constants.PITCH_SUSPENDED.equals(status) || Constants.PITCH_CLOSED.equals(status)) {
                displayStatus = "Đình chỉ / Đóng";
                color = Color.parseColor("#F44336");
                binding.btnApprove.setEnabled(true);
                binding.btnSuspend.setEnabled(false);
            } else {
                displayStatus = "Đang bảo trì / Chờ duyệt";
                color = Color.parseColor("#FF9800");
                binding.btnApprove.setEnabled(true);
                binding.btnSuspend.setEnabled(true);
            }

            binding.tvPitchStatus.setText(displayStatus);
            binding.tvPitchStatus.setTextColor(color);

            binding.btnApprove.setOnClickListener(v -> listener.onStatusChange(pitch, Constants.PITCH_AVAILABLE));
            binding.btnSuspend.setOnClickListener(v -> listener.onStatusChange(pitch, Constants.PITCH_SUSPENDED));
        }
    }
}
