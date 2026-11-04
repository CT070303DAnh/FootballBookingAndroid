package com.example.footballbooking.ui.common.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.footballbooking.data.model.ChatConversation;
import com.example.footballbooking.databinding.ItemChatConversationBinding;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Adapter dùng chung cho hộp thư User và Chủ sân. */
public class ChatConversationAdapter
        extends ListAdapter<ChatConversation, ChatConversationAdapter.ViewHolder> {

    public interface OnConversationClickListener {
        void onConversationClick(ChatConversation conversation);
    }

    private final boolean ownerMode;
    private final String currentUserId;
    private final OnConversationClickListener listener;
    private static final DiffUtil.ItemCallback<ChatConversation> DIFF =
            new DiffUtil.ItemCallback<ChatConversation>() {
                @Override
                public boolean areItemsTheSame(@NonNull ChatConversation oldItem,
                                               @NonNull ChatConversation newItem) {
                    return Objects.equals(oldItem.getConversationId(), newItem.getConversationId());
                }

                @Override
                public boolean areContentsTheSame(@NonNull ChatConversation oldItem,
                                                  @NonNull ChatConversation newItem) {
                    return Objects.equals(oldItem.getLastMessage(), newItem.getLastMessage())
                            && Objects.equals(oldItem.getLastMessageAt(), newItem.getLastMessageAt())
                            && Objects.equals(oldItem.getPitchName(), newItem.getPitchName())
                            && Objects.equals(oldItem.getCustomerName(), newItem.getCustomerName())
                            && oldItem.getUnreadForCustomer() == newItem.getUnreadForCustomer()
                            && oldItem.getUnreadForOwner() == newItem.getUnreadForOwner();
                }
            };

    public ChatConversationAdapter(boolean ownerMode, String currentUserId,
                                   OnConversationClickListener listener) {
        super(DIFF);
        this.ownerMode = ownerMode;
        this.currentUserId = currentUserId;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(ItemChatConversationBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(getItem(position), ownerMode, currentUserId, listener);
    }

    public void submitSorted(List<ChatConversation> items) {
        submitList(items);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemChatConversationBinding binding;

        ViewHolder(ItemChatConversationBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(ChatConversation item, boolean ownerMode, String currentUserId,
                  OnConversationClickListener listener) {
            binding.tvConversationTitle.setText(ownerMode
                    ? item.getCustomerName() : item.getPitchName());
            String slot = item.getBookingDate() + " • "
                    + item.getStartTime() + " - " + item.getEndTime();
            String context = ownerMode
                    ? item.getPitchName() + " • " + slot
                    : "Trao đổi với chủ sân • " + slot;
            binding.tvConversationContext.setText(context);
            binding.tvConversationLastMessage.setText(item.getLastMessage());

            long unread = item.getUnreadCountFor(currentUserId);
            if (unread > 0) {
                binding.tvConversationUnread.setVisibility(android.view.View.VISIBLE);
                binding.tvConversationUnread.setText(unread > 99 ? "99+" : String.valueOf(unread));
            } else {
                binding.tvConversationUnread.setVisibility(android.view.View.GONE);
            }

            if (item.getLastMessageAt() != null) {
                binding.tvConversationTime.setText(new SimpleDateFormat(
                        "dd/MM HH:mm", Locale.getDefault()).format(
                        new Date(item.getLastMessageAt().toDate().getTime())));
            } else {
                binding.tvConversationTime.setText("");
            }
            binding.getRoot().setOnClickListener(v -> listener.onConversationClick(item));
        }
    }
}
