package com.example.footballbooking.data.repository;

import androidx.annotation.NonNull;

import com.example.footballbooking.data.model.Booking;
import com.example.footballbooking.data.model.ChatConversation;
import com.example.footballbooking.utils.Constants;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.Source;
import com.google.firebase.firestore.WriteBatch;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/** Repository cho hội thoại gắn với từng đơn đặt sân. */
public class ChatRepository {
    private static final int MAX_MESSAGE_LENGTH = 1000;
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private ListenerRegistration messagesListener;

    /** Mỗi booking là một hội thoại riêng, giữ tương thích với thiết kế cũ. */
    public static String conversationId(String bookingId) {
        return bookingId == null ? "" : bookingId;
    }

    public Task<DocumentSnapshot> getBooking(String bookingId) {
        return db.collection(Constants.COL_BOOKINGS).document(bookingId).get(Source.SERVER);
    }

    public Task<DocumentSnapshot> getConversation(String bookingId) {
        return db.collection(Constants.COL_CONVERSATIONS)
                .document(conversationId(bookingId)).get(Source.SERVER);
    }

    public Task<Void> ensureConversationForBooking(Booking booking) {
        if (booking == null || booking.getBookingId() == null
                || booking.getCustomerId() == null || booking.getPitchOwnerId() == null) {
            return Tasks.forException(new IllegalArgumentException("Booking chat không hợp lệ"));
        }
        return ensureConversation(booking.getBookingId(), booking);
    }

    /** Tạo conversation nếu chưa có; không tạo thẻ sự kiện tự động. */
    public Task<Void> ensureConversation(String bookingId, Booking booking) {
        DocumentReference conversation = conversationRef(bookingId);
        Map<String, Object> data = new HashMap<>();
        data.put("bookingId", bookingId);
        data.put("customerId", booking.getCustomerId());
        data.put("ownerId", booking.getPitchOwnerId());
        data.put("pitchId", booking.getPitchId());
        data.put("participants", Arrays.asList(booking.getCustomerId(), booking.getPitchOwnerId()));
        data.put("pitchName", safe(booking.getPitchName(), "Sân bóng"));
        data.put("customerName", safe(booking.getCustomerName(), "Khách hàng"));
        data.put("bookingDate", safe(booking.getBookingDate(), ""));
        data.put("startTime", safe(booking.getStartTime(), ""));
        data.put("endTime", safe(booking.getEndTime(), ""));
        data.put("status", "active");
        data.put("lastMessage", "");
        data.put("lastSenderId", "");
        data.put("unreadForCustomer", 0L);
        data.put("unreadForOwner", 0L);
        data.put("lastMessageAt", FieldValue.serverTimestamp());
        data.put("createdAt", FieldValue.serverTimestamp());
        data.put("updatedAt", FieldValue.serverTimestamp());

        return conversation.set(data).continueWithTask(writeTask -> {
            if (writeTask.isSuccessful()) return Tasks.forResult(null);
            // Gọi lặp trên conversation đã tồn tại là trường hợp bình thường.
            return conversation.get(Source.SERVER).continueWithTask(readTask -> {
                if (readTask.isSuccessful() && readTask.getResult().exists()) {
                    return Tasks.forResult(null);
                }
                return Tasks.forException(writeTask.getException() != null
                        ? writeTask.getException()
                        : new IllegalStateException("Không tạo được hội thoại"));
            });
        });
    }

    public Task<Void> sendMessage(String bookingId, String senderId, String content) {
        String normalized = content == null ? "" : content.trim();
        if (normalized.isEmpty()) {
            return Tasks.forException(new IllegalArgumentException("Tin nhắn không được để trống"));
        }
        if (normalized.length() > MAX_MESSAGE_LENGTH) {
            return Tasks.forException(new IllegalArgumentException(
                    "Tin nhắn không được vượt quá " + MAX_MESSAGE_LENGTH + " ký tự"));
        }

        DocumentReference conversation = conversationRef(bookingId);
        return conversation.get(Source.SERVER).continueWithTask(readTask -> {
            if (!readTask.isSuccessful() || readTask.getResult() == null
                    || !readTask.getResult().exists()) {
                return Tasks.forException(new IllegalStateException(
                        "Hội thoại không tồn tại"));
            }

            DocumentSnapshot snapshot = readTask.getResult();
            String customerId = snapshot.getString("customerId");
            String ownerId = snapshot.getString("ownerId");
            boolean senderIsCustomer = senderId.equals(customerId);
            boolean senderIsOwner = senderId.equals(ownerId);
            if (!senderIsCustomer && !senderIsOwner) {
                return Tasks.forException(new SecurityException(
                        "Bạn không thuộc hội thoại này"));
            }

            DocumentReference message = conversation
                    .collection(Constants.SUB_MESSAGES).document();
            Map<String, Object> messageData = new HashMap<>();
            messageData.put("senderId", senderId);
            messageData.put("content", normalized);
            messageData.put("type", "text");
            messageData.put("createdAt", FieldValue.serverTimestamp());

            Map<String, Object> conversationUpdate = new HashMap<>();
            conversationUpdate.put("lastMessage", normalized);
            conversationUpdate.put("lastSenderId", senderId);
            conversationUpdate.put("lastMessageAt", FieldValue.serverTimestamp());
            conversationUpdate.put("updatedAt", FieldValue.serverTimestamp());
            if (senderIsCustomer) {
                conversationUpdate.put("unreadForOwner", FieldValue.increment(1));
            } else {
                conversationUpdate.put("unreadForCustomer", FieldValue.increment(1));
            }

            WriteBatch batch = db.batch();
            batch.set(message, messageData);
            batch.update(conversation, conversationUpdate);
            return batch.commit();
        });
    }

    /** Đánh dấu toàn bộ tin nhắn của người dùng hiện tại là đã đọc. */
    public Task<Void> markAsRead(String bookingId, String userId) {
        DocumentReference conversation = conversationRef(bookingId);
        return conversation.get(Source.SERVER).continueWithTask(readTask -> {
            if (!readTask.isSuccessful() || readTask.getResult() == null
                    || !readTask.getResult().exists()) {
                return Tasks.forException(new IllegalStateException(
                        "Hội thoại không tồn tại"));
            }
            DocumentSnapshot snapshot = readTask.getResult();
            String customerId = snapshot.getString("customerId");
            String ownerId = snapshot.getString("ownerId");
            String field;
            if (userId.equals(customerId)) {
                field = "unreadForCustomer";
            } else if (userId.equals(ownerId)) {
                field = "unreadForOwner";
            } else {
                return Tasks.forException(new SecurityException(
                        "Bạn không thuộc hội thoại này"));
            }
            return conversation.update(field, 0L);
        });
    }

    public ListenerRegistration listenConversations(String userId,
                                                     EventListener<QuerySnapshot> listener) {
        return db.collection(Constants.COL_CONVERSATIONS)
                .whereArrayContains("participants", userId)
                .whereEqualTo("status", "active")
                .limit(50)
                .addSnapshotListener(listener);
    }

    /** Tổng số tin chưa đọc để hiển thị badge trên taskbar. */
    public ListenerRegistration listenUnreadCount(String userId,
                                                   EventListener<Integer> listener) {
        return listenConversations(userId, (snapshots, error) -> {
            if (error != null || snapshots == null) {
                listener.onEvent(0, error);
                return;
            }
            long unread = 0L;
            for (DocumentSnapshot snapshot : snapshots.getDocuments()) {
                unread += ChatConversation.fromSnapshot(snapshot)
                        .getUnreadCountFor(userId);
            }
            listener.onEvent((int) Math.min(unread, Integer.MAX_VALUE), null);
        });
    }

    public ListenerRegistration listenMessages(String bookingId,
                                               EventListener<QuerySnapshot> listener) {
        detachMessagesListener();
        messagesListener = conversationRef(bookingId)
                .collection(Constants.SUB_MESSAGES)
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .limitToLast(100)
                .addSnapshotListener(listener);
        return messagesListener;
    }

    public void detachMessagesListener() {
        if (messagesListener != null) {
            messagesListener.remove();
            messagesListener = null;
        }
    }

    private DocumentReference conversationRef(@NonNull String bookingId) {
        return db.collection(Constants.COL_CONVERSATIONS).document(bookingId);
    }

    private String safe(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }
}
