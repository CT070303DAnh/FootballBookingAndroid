package com.example.footballbooking.ui.common.chat;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.footballbooking.data.model.Booking;
import com.example.footballbooking.data.model.ChatMessage;
import com.example.footballbooking.data.repository.ChatRepository;
import com.example.footballbooking.databinding.ActivityChatBinding;
import com.example.footballbooking.ui.common.adapter.ChatMessageAdapter;
import com.example.footballbooking.utils.Constants;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.List;

/** Màn hình chat trực tiếp giữa User và Owner. */
public class ChatActivity extends AppCompatActivity {
    private static final String TAG = "ChatActivity";
    private ActivityChatBinding binding;
    private ChatRepository repository;
    private ChatMessageAdapter adapter;
    private ListenerRegistration messagesListener;
    private String conversationId;
    private String currentUserId;
    private boolean chatEnabled;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityChatBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        conversationId = getIntent().getStringExtra(Constants.EXTRA_CONVERSATION_ID);
        String bookingId = getIntent().getStringExtra(Constants.EXTRA_BOOKING_ID);
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        currentUserId = user == null ? null : user.getUid();

        if (currentUserId == null || (isBlank(conversationId) && isBlank(bookingId))) {
            finish();
            return;
        }

        repository = new ChatRepository();
        adapter = new ChatMessageAdapter(currentUserId);
        binding.rvChatMessages.setLayoutManager(new LinearLayoutManager(this));
        binding.rvChatMessages.setAdapter(adapter);
        binding.btnChatBack.setOnClickListener(v -> finish());
        binding.btnChatSend.setOnClickListener(v -> sendMessage());
        binding.etChatMessage.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                binding.btnChatSend.setEnabled(chatEnabled && s != null
                        && s.toString().trim().length() > 0);
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        if (!isBlank(conversationId)) {
            loadConversation();
        } else {
            loadBooking(bookingId);
        }
    }

    private void loadConversation() {
        setLoading(true);
        repository.getConversation(conversationId)
                .addOnSuccessListener(this::onConversationLoaded)
                .addOnFailureListener(error -> showError("Không tải được cuộc trò chuyện"));
    }

    private void onConversationLoaded(DocumentSnapshot snapshot) {
        if (!snapshot.exists()) {
            showError("Cuộc trò chuyện không tồn tại");
            return;
        }
        Object participants = snapshot.get("participants");
        if (!(participants instanceof List) || !((List<?>) participants).contains(currentUserId)) {
            showError("Bạn không có quyền xem cuộc trò chuyện này");
            return;
        }

        String ownerId = snapshot.getString("ownerId");
        boolean ownerMode = currentUserId.equals(ownerId);
        String title = ownerMode ? safe(snapshot.getString("customerName"), "Khách hàng")
                : safe(snapshot.getString("pitchName"), "Sân bóng");
        binding.tvChatTitle.setText(title);
        String date = safe(snapshot.getString("bookingDate"), "");
        String start = safe(snapshot.getString("startTime"), "");
        String end = safe(snapshot.getString("endTime"), "");
        binding.tvChatSubtitle.setText(date + " • " + start + " - " + end);
        markConversationReadAndStart();
    }

    private void loadBooking(String bookingId) {
        setLoading(true);
        repository.getBooking(bookingId)
                .addOnSuccessListener(this::onBookingLoaded)
                .addOnFailureListener(error -> showError("Không tải được đơn đặt sân"));
    }

    private void onBookingLoaded(DocumentSnapshot snapshot) {
        if (!snapshot.exists()) {
            showError("Đơn đặt sân không tồn tại");
            return;
        }

        String customerId = snapshot.getString("customerId");
        String ownerId = snapshot.getString("pitchOwnerId");
        boolean participant = currentUserId.equals(customerId) || currentUserId.equals(ownerId);
        if (!participant) {
            showError("Bạn không có quyền xem đơn đặt sân này");
            return;
        }

        Booking booking = snapshot.toObject(Booking.class);
        if (booking == null) {
            showError("Dữ liệu đơn đặt sân không hợp lệ");
            return;
        }
        booking.setBookingId(snapshot.getId());
        conversationId = ChatRepository.conversationId(snapshot.getId());

        String pitchName = safe(snapshot.getString("pitchName"), "Sân bóng");
        String date = safe(snapshot.getString("bookingDate"), "");
        String start = safe(snapshot.getString("startTime"), "");
        String end = safe(snapshot.getString("endTime"), "");
        binding.tvChatTitle.setText(pitchName);
        binding.tvChatSubtitle.setText(date + " • " + start + " - " + end);

        repository.ensureConversation(conversationId, booking)
                .addOnSuccessListener(ignored -> markConversationReadAndStart())
                .addOnFailureListener(error -> {
                    Log.e(TAG, "Cannot initialize conversation", error);
                    String detail = error.getMessage();
                    showError(detail == null || detail.trim().isEmpty()
                            ? "Không thể mở cuộc trò chuyện"
                            : "Không thể mở cuộc trò chuyện: " + detail);
                });
    }

    private void markConversationReadAndStart() {
        repository.markAsRead(conversationId, currentUserId)
                .addOnCompleteListener(ignored -> startMessagesListener());
    }

    private void startMessagesListener() {
        chatEnabled = true;
        setLoading(false);
        binding.etChatMessage.setEnabled(true);
        binding.btnChatSend.setEnabled(false);
        messagesListener = repository.listenMessages(conversationId, (snapshots, error) -> {
            if (error != null) {
                Log.e(TAG, "Message listener failed", error);
                showError("Không thể đồng bộ tin nhắn");
                return;
            }
            List<ChatMessage> messages = new ArrayList<>();
            if (snapshots != null) {
                for (DocumentSnapshot document : snapshots.getDocuments()) {
                    messages.add(ChatMessage.fromSnapshot(document));
                }
            }
            adapter.submitList(messages, () -> {
                if (!messages.isEmpty()) {
                    binding.rvChatMessages.scrollToPosition(messages.size() - 1);
                    ChatMessage latest = messages.get(messages.size() - 1);
                    if (!currentUserId.equals(latest.getSenderId())) {
                        repository.markAsRead(conversationId, currentUserId);
                    }
                }
            });
        });
    }

    private void sendMessage() {
        String content = binding.etChatMessage.getText() == null
                ? "" : binding.etChatMessage.getText().toString().trim();
        if (!chatEnabled || content.isEmpty()) return;
        if (content.length() > 1000) {
            binding.etChatMessage.setError("Tối đa 1000 ký tự");
            return;
        }

        setSending(true);
        repository.sendMessage(conversationId, currentUserId, content)
                .addOnSuccessListener(ignored -> {
                    binding.etChatMessage.setText("");
                    setSending(false);
                })
                .addOnFailureListener(error -> {
                    Log.e(TAG, "Cannot send message", error);
                    setSending(false);
                    Snackbar.make(binding.getRoot(), error.getMessage() == null
                            ? "Gửi tin nhắn thất bại" : error.getMessage(),
                            Snackbar.LENGTH_LONG).show();
                });
    }

    private void setLoading(boolean loading) {
        binding.progressChat.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (loading) {
            binding.etChatMessage.setEnabled(false);
            binding.btnChatSend.setEnabled(false);
        }
    }

    private void setSending(boolean sending) {
        binding.btnChatSend.setEnabled(!sending && chatEnabled
                && binding.etChatMessage.getText() != null
                && binding.etChatMessage.getText().toString().trim().length() > 0);
        binding.etChatMessage.setEnabled(!sending && chatEnabled);
        binding.progressChat.setVisibility(sending ? View.VISIBLE : View.GONE);
    }

    private void showError(String message) {
        setLoading(false);
        chatEnabled = false;
        binding.etChatMessage.setEnabled(false);
        binding.btnChatSend.setEnabled(false);
        Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
    }

    private String safe(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    @Override
    protected void onDestroy() {
        if (messagesListener != null) messagesListener.remove();
        if (repository != null) repository.detachMessagesListener();
        super.onDestroy();
        binding = null;
    }
}
