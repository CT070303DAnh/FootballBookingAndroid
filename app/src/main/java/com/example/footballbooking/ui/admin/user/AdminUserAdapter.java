package com.example.footballbooking.ui.admin.user;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.footballbooking.R;
import com.example.footballbooking.data.model.User;
import com.example.footballbooking.databinding.ItemAdminUserBinding;
import com.example.footballbooking.utils.Constants;

import java.util.ArrayList;
import java.util.List;

public class AdminUserAdapter extends RecyclerView.Adapter<AdminUserAdapter.UserViewHolder> {

    private final Context context;
    private List<User> userList = new ArrayList<>();
    private final OnUserStatusChangeListener listener;

    public interface OnUserStatusChangeListener {
        void onStatusChange(User user, String newStatus);
    }

    public AdminUserAdapter(Context context, OnUserStatusChangeListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void submitList(List<User> newList) {
        this.userList = newList;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public UserViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemAdminUserBinding binding = ItemAdminUserBinding.inflate(
                LayoutInflater.from(context), parent, false);
        return new UserViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull UserViewHolder holder, int position) {
        User user = userList.get(position);
        holder.bind(user);
    }

    @Override
    public int getItemCount() {
        return userList != null ? userList.size() : 0;
    }

    class UserViewHolder extends RecyclerView.ViewHolder {
        private final ItemAdminUserBinding binding;

        public UserViewHolder(ItemAdminUserBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(User user) {
            binding.tvUserName.setText(user.getDisplayName() != null && !user.getDisplayName().isEmpty() ? user.getDisplayName() : "No Name");
            binding.tvUserEmail.setText(user.getEmail());
            binding.tvUserPhone.setText(user.getPhoneNumber() != null ? user.getPhoneNumber() : "");
            
            String roleText = "CUSTOMER";
            if (Constants.ROLE_ADMIN.equals(user.getRole())) roleText = "ADMIN";
            else if (Constants.ROLE_OWNER.equals(user.getRole())) roleText = "OWNER";
            else if (Constants.ROLE_OWNER_PENDING.equals(user.getRole())) roleText = "PENDING OWNER";
            binding.tvUserRole.setText(roleText);

            if (user.getAvatarUrl() != null && !user.getAvatarUrl().isEmpty()) {
                Glide.with(context)
                        .load(user.getAvatarUrl())
                        .placeholder(R.drawable.ic_launcher_background)
                        .error(R.drawable.ic_launcher_background)
                        .circleCrop()
                        .into(binding.ivUserAvatar);
            } else {
                binding.ivUserAvatar.setImageResource(R.drawable.ic_launcher_background);
            }

            boolean isActive = Constants.STATUS_ACTIVE.equals(user.getStatus());
            binding.tvUserStatus.setText(isActive ? "Active" : "Blocked");
            binding.tvUserStatus.setTextColor(isActive ? Color.parseColor("#4CAF50") : Color.parseColor("#F44336"));

            binding.btnToggleStatus.setText(isActive ? "Khóa" : "Mở khóa");
            binding.btnToggleStatus.setOnClickListener(v -> {
                String newStatus = isActive ? Constants.STATUS_BLOCKED : Constants.STATUS_ACTIVE;
                listener.onStatusChange(user, newStatus);
            });
            
            // Prevent admin from blocking themselves
            if (Constants.ROLE_ADMIN.equals(user.getRole())) {
                binding.btnToggleStatus.setEnabled(false);
            } else {
                binding.btnToggleStatus.setEnabled(true);
            }
        }
    }
}
