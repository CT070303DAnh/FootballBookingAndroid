package com.example.footballbooking.data.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;

/** Message text hoặc message hệ thống gắn với một booking. */
public class ChatMessage {
    private String messageId;
    private String senderId;
    private String content;
    private String type;
    private String eventType;
    private String bookingId;
    private String pitchName;
    private String customerName;
    private String bookingDate;
    private String startTime;
    private String endTime;
    private double totalAmount;
    private String bookingStatus;
    private String paymentStatus;
    private Timestamp createdAt;

    public ChatMessage() {}

    public static ChatMessage fromSnapshot(DocumentSnapshot snapshot) {
        ChatMessage message = new ChatMessage();
        message.messageId = snapshot.getId();
        message.senderId = snapshot.getString("senderId");
        message.content = snapshot.getString("content");
        message.type = snapshot.getString("type");
        message.eventType = snapshot.getString("eventType");
        message.bookingId = snapshot.getString("bookingId");
        message.pitchName = snapshot.getString("pitchName");
        message.customerName = snapshot.getString("customerName");
        message.bookingDate = snapshot.getString("bookingDate");
        message.startTime = snapshot.getString("startTime");
        message.endTime = snapshot.getString("endTime");
        Double amount = snapshot.getDouble("totalAmount");
        message.totalAmount = amount == null ? 0 : amount;
        message.bookingStatus = snapshot.getString("bookingStatus");
        message.paymentStatus = snapshot.getString("paymentStatus");
        message.createdAt = snapshot.getTimestamp("createdAt");
        return message;
    }

    public String getMessageId() { return messageId; }
    public String getSenderId() { return senderId; }
    public String getContent() { return content; }
    public String getType() { return type; }
    public String getEventType() { return eventType; }
    public String getBookingId() { return bookingId; }
    public String getPitchName() { return pitchName; }
    public String getCustomerName() { return customerName; }
    public String getBookingDate() { return bookingDate; }
    public String getStartTime() { return startTime; }
    public String getEndTime() { return endTime; }
    public double getTotalAmount() { return totalAmount; }
    public String getBookingStatus() { return bookingStatus; }
    public String getPaymentStatus() { return paymentStatus; }
    public Timestamp getCreatedAt() { return createdAt; }
}
