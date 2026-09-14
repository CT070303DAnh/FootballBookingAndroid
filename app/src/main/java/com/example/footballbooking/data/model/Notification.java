package com.example.footballbooking.data.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.ServerTimestamp;

/** Model ánh xạ với Firestore collection "notifications". */
public class Notification {

    @DocumentId
    private String notificationId;
    private String recipientId;   // userId nhận thông báo
    private String title;
    private String body;
    private String type;          // "booking_approved", "booking_rejected", "reminder"
    private String bookingId;     // Nullable — link đến booking liên quan
    private boolean isRead;

    @ServerTimestamp
    private Timestamp createdAt;

    public Notification() {}

    // --- Getters & Setters ---
    public String getNotificationId() { return notificationId; }
    public void setNotificationId(String notificationId) { this.notificationId = notificationId; }

    public String getRecipientId() { return recipientId; }
    public void setRecipientId(String recipientId) { this.recipientId = recipientId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getBookingId() { return bookingId; }
    public void setBookingId(String bookingId) { this.bookingId = bookingId; }

    public boolean isRead() { return isRead; }
    public void setRead(boolean read) { isRead = read; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
}
