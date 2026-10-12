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
    private final java.util.function.Consumer<User> approvalListener;
    private final java.util.function.Consumer<User> rejectionListener;
    private final java.util.function.Consumer<User> detailsListener;
    private final java.util.function.Consumer<User> editListener;
    private final java.util.function.Consumer<User> deleteListener;
    private boolean approvalInProgress;
    private boolean statusChangeInProgress;
    private boolean crudInProgress;

    public interface OnUserStatusChangeListener {
        void onStatusChange(User user, String newStatus);
    }

    public AdminUserAdapter(Context context, OnUserStatusChangeListener listener,
                            java.util.function.Consumer<User> approvalListener,
                            java.util.function.Consumer<User> rejectionListener,
                            java.util.function.Consumer<User> detailsListener,
                            java.util.function.Consumer<User> editListener,
                            java.util.function.Consumer<User> deleteListener) {
        this.context = context;
        this.listener = listener;
        this.approvalListener = approvalListener;
        this.rejectionListener = rejectionListener;
        this.detailsListener = detailsListener;
        this.editListener = editListener;
        this.deleteListener = deleteListener;
    }

    public void setApprovalInProgress(boolean inProgress) {
        if (approvalInProgress == inProgress) return;
        approvalInProgress = inProgress;
        notifyDataSetChanged();
    }

    public void setStatusChangeInProgress(boolean inProgress) {
        if (statusChangeInProgress == inProgress) return;
        statusChangeInProgress = inProgress;
        notifyDataSetChanged();
    }

    public void setCrudInProgress(boolean inProgress) {
        if (crudInProgress == inProgress) return;
        crudInProgress = inProgress;
        notifyDataSetChanged();
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
            binding.btnApproveOwner.setVisibility(Constants.ROLE_OWNER_PENDING.equals(user.getRole())
                    ? android.view.View.VISIBLE : android.view.View.GONE);
            binding.btnApproveOwner.setEnabled(isActive && !approvalInProgress);
            binding.btnApproveOwner.setOnClickListener(v -> approvalListener.accept(user));
            binding.btnRejectOwner.setVisibility(Constants.ROLE_OWNER_PENDING.equals(user.getRole())
                    ? android.view.View.VISIBLE : android.view.View.GONE);
            binding.btnRejectOwner.setEnabled(isActive && !approvalInProgress);
            binding.btnRejectOwner.setOnClickListener(v -> rejectionListener.accept(user));
            binding.btnViewDetails.setOnClickListener(v -> detailsListener.accept(user));
            binding.btnEditUser.setOnClickListener(v -> editListener.accept(user));
            binding.btnDeleteUser.setOnClickListener(v -> deleteListener.accept(user));
            binding.tvUserStatus.setText(isActive ? "Active" : "Blocked");
            binding.tvUserStatus.setTextColor(isActive ? Color.parseColor("#4CAF50") : Color.parseColor("#F44336"));

            binding.btnToggleStatus.setText(isActive ? "Khóa" : "Mở khóa");
            binding.btnToggleStatus.setOnClickListener(v -> {
                String newStatus = isActive ? Constants.STATUS_BLOCKED : Constants.STATUS_ACTIVE;
                listener.onStatusChange(user, newStatus);
            });
            binding.btnToggleStatus.setEnabled(!statusChangeInProgress && !Constants.ROLE_ADMIN.equals(user.getRole()));
            boolean isAdmin = Constants.ROLE_ADMIN.equals(user.getRole());
            binding.btnEditUser.setEnabled(!crudInProgress && !isAdmin);
            binding.btnDeleteUser.setEnabled(!crudInProgress && !isAdmin);
            binding.getRoot().setOnClickListener(v -> detailsListener.accept(user));
            
            // Prevent admin from blocking themselves
            if (isAdmin) {
                binding.btnToggleStatus.setEnabled(false);
            }
        }
    }
}
