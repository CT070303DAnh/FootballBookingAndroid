package com.example.footballbooking.data.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;

/** Một hội thoại gắn với một đơn đặt sân cụ thể. */
public class ChatConversation {
    private String conversationId;
    private String bookingId;
    private String customerId;
    private String ownerId;
    private String pitchId;
    private String pitchName;
    private String customerName;
    private String bookingDate;
    private String startTime;
    private String endTime;
    private String lastMessage;
    private String lastSenderId;
    private Timestamp lastMessageAt;
    private long unreadForCustomer;
    private long unreadForOwner;

    public static ChatConversation fromSnapshot(DocumentSnapshot snapshot) {
        ChatConversation item = new ChatConversation();
        item.conversationId = snapshot.getId();
        item.bookingId = safe(snapshot.getString("bookingId"), snapshot.getId());
        item.customerId = safe(snapshot.getString("customerId"), "");
        item.ownerId = safe(snapshot.getString("ownerId"), "");
        item.pitchId = safe(snapshot.getString("pitchId"), "");
        item.pitchName = safe(snapshot.getString("pitchName"), "Sân bóng");
        item.customerName = safe(snapshot.getString("customerName"), "Khách hàng");
        item.bookingDate = safe(snapshot.getString("bookingDate"), "");
        item.startTime = safe(snapshot.getString("startTime"), "");
        item.endTime = safe(snapshot.getString("endTime"), "");
        item.lastMessage = safe(snapshot.getString("lastMessage"), "Chưa có tin nhắn");
        item.lastSenderId = safe(snapshot.getString("lastSenderId"), "");
        item.lastMessageAt = snapshot.getTimestamp("lastMessageAt");
        item.unreadForCustomer = numberOrZero(snapshot.getLong("unreadForCustomer"));
        item.unreadForOwner = numberOrZero(snapshot.getLong("unreadForOwner"));
        return item;
    }

    private static String safe(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    private static long numberOrZero(Long value) {
        return value == null ? 0L : Math.max(0L, value);
    }

    public String getConversationId() { return conversationId; }
    public String getBookingId() { return bookingId; }
    public String getCustomerId() { return customerId; }
    public String getOwnerId() { return ownerId; }
    public String getPitchId() { return pitchId; }
    public String getPitchName() { return pitchName; }
    public String getCustomerName() { return customerName; }
    public String getBookingDate() { return bookingDate; }
    public String getStartTime() { return startTime; }
    public String getEndTime() { return endTime; }
    public String getLastMessage() { return lastMessage; }
    public String getLastSenderId() { return lastSenderId; }
    public Timestamp getLastMessageAt() { return lastMessageAt; }

    public long getUnreadForCustomer() { return unreadForCustomer; }
    public long getUnreadForOwner() { return unreadForOwner; }

    public long getUnreadCountFor(String userId) {
        if (userId == null) return 0L;
        if (userId.equals(customerId)) return unreadForCustomer;
        if (userId.equals(ownerId)) return unreadForOwner;
        return 0L;
    }
}
