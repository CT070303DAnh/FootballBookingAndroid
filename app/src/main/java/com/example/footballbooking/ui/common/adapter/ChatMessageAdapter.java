package com.example.footballbooking.ui.common.adapter;

import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.footballbooking.R;
import com.example.footballbooking.data.model.ChatMessage;
import com.example.footballbooking.databinding.ItemChatMessageBinding;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;

/** Adapter hiển thị tin nhắn văn bản trong hội thoại của từng booking. */
public class ChatMessageAdapter extends ListAdapter<ChatMessage, ChatMessageAdapter.ViewHolder> {
    private final String currentUserId;

    private static final DiffUtil.ItemCallback<ChatMessage> DIFF =
            new DiffUtil.ItemCallback<ChatMessage>() {
                @Override
                public boolean areItemsTheSame(@NonNull ChatMessage oldItem,
                                               @NonNull ChatMessage newItem) {
                    return Objects.equals(oldItem.getMessageId(), newItem.getMessageId());
                }

                @Override
                public boolean areContentsTheSame(@NonNull ChatMessage oldItem,
                                                  @NonNull ChatMessage newItem) {
                    return Objects.equals(oldItem.getContent(), newItem.getContent())
                            && Objects.equals(oldItem.getSenderId(), newItem.getSenderId())
                            && Objects.equals(oldItem.getCreatedAt(), newItem.getCreatedAt());
                }
            };

    public ChatMessageAdapter(String currentUserId) {
        super(DIFF);
        this.currentUserId = currentUserId;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(ItemChatMessageBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(getItem(position), currentUserId);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemChatMessageBinding binding;

        ViewHolder(ItemChatMessageBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(ChatMessage message, String currentUserId) {
            boolean mine = currentUserId != null && currentUserId.equals(message.getSenderId());
            binding.tvChatMessage.setText(message.getContent());
            binding.tvChatTime.setText(formatTime(message));

            android.widget.FrameLayout.LayoutParams params =
                    (android.widget.FrameLayout.LayoutParams) binding.chatBubble.getLayoutParams();
            params.width = ViewGroup.LayoutParams.WRAP_CONTENT;
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            params.gravity = mine ? Gravity.END : Gravity.START;
            params.setMarginStart(dp(binding.getRoot(), mine ? 56 : 8));
            params.setMarginEnd(dp(binding.getRoot(), mine ? 8 : 56));
            params.topMargin = dp(binding.getRoot(), 4);
            params.bottomMargin = dp(binding.getRoot(), 4);
            binding.chatBubble.setLayoutParams(params);
            binding.chatBubble.setCardBackgroundColor(ContextCompat.getColor(
                    binding.getRoot().getContext(),
                    mine ? R.color.primary_variant : R.color.background_surface));
            binding.chatBubble.setStrokeWidth(0);
        }

        private int dp(View view, int value) {
            return Math.round(value * view.getResources().getDisplayMetrics().density);
        }
    }

    private static String formatTime(ChatMessage message) {
        if (message.getCreatedAt() == null) return "Đang gửi...";
        return new SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
                .format(new Date(message.getCreatedAt().toDate().getTime()));
    }
}
