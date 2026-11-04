package com.example.footballbooking.ui.common.chat;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.footballbooking.data.model.ChatConversation;
import com.example.footballbooking.data.repository.ChatRepository;
import com.example.footballbooking.databinding.FragmentChatInboxBinding;
import com.example.footballbooking.ui.common.adapter.ChatConversationAdapter;
import com.example.footballbooking.utils.Constants;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Hộp thư độc lập cho cả User và Chủ sân. */
public class ChatInboxFragment extends Fragment {
    private FragmentChatInboxBinding binding;
    private ChatRepository repository;
    private ListenerRegistration conversationsListener;
    private ChatConversationAdapter adapter;
    private boolean ownerMode;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ownerMode = getArguments() != null
                && getArguments().getBoolean("ownerMode", false);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentChatInboxBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        repository = new ChatRepository();
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        adapter = new ChatConversationAdapter(ownerMode,
                currentUser == null ? null : currentUser.getUid(),
                this::openConversation);
        binding.rvChatInbox.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvChatInbox.setAdapter(adapter);
        listenForConversations();
    }

    private void listenForConversations() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            showEmpty(true);
            return;
        }

        binding.progressChatInbox.setVisibility(View.VISIBLE);
        conversationsListener = repository.listenConversations(user.getUid(),
                (snapshots, error) -> {
                    if (!isAdded() || binding == null) return;
                    binding.progressChatInbox.setVisibility(View.GONE);
                    if (error != null) {
                        showEmpty(true);
                        Snackbar.make(binding.getRoot(),
                                "Không tải được danh sách tin nhắn",
                                Snackbar.LENGTH_LONG).show();
                        return;
                    }

                    List<ChatConversation> conversations = new ArrayList<>();
                    if (snapshots != null) {
                        for (DocumentSnapshot snapshot : snapshots.getDocuments()) {
                            // Bỏ qua các document của mô hình direct-thread cũ
                            // nếu dữ liệu thử nghiệm trước đó còn tồn tại.
                            if (snapshot.getString("bookingId") == null) continue;
                            conversations.add(ChatConversation.fromSnapshot(snapshot));
                        }
                    }
                    sortByLatest(conversations);
                    adapter.submitSorted(conversations);
                    showEmpty(conversations.isEmpty());
                });
    }

    private void sortByLatest(List<ChatConversation> items) {
        Collections.sort(items, Comparator.comparing(
                ChatConversation::getLastMessageAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
    }

    private void openConversation(ChatConversation conversation) {
        if (conversation == null || conversation.getConversationId() == null) return;
        Intent intent = new Intent(requireContext(), ChatActivity.class);
        intent.putExtra(Constants.EXTRA_CONVERSATION_ID, conversation.getConversationId());
        startActivity(intent);
    }

    private void showEmpty(boolean empty) {
        if (binding == null) return;
        binding.tvChatInboxEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.rvChatInbox.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    @Override
    public void onDestroyView() {
        if (conversationsListener != null) {
            conversationsListener.remove();
            conversationsListener = null;
        }
        super.onDestroyView();
        binding = null;
    }
}
