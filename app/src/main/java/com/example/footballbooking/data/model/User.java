package com.example.footballbooking.data.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.ServerTimestamp;
import com.example.footballbooking.utils.Constants;

/**
 * Model ánh xạ với Firestore collection "users".
 * Dùng @DocumentId để tự động map document ID vào field uid.
 * Firestore yêu cầu constructor rỗng (no-arg) để deserialize.
 */
public class User {

    @DocumentId
    private String uid;
    private String email;
    private String displayName;
    private String phoneNumber;
    private String avatarUrl;
    private String role;        // "customer" hoặc "admin" — xem Constants.java
    private String fcmToken;    // FCM token để gửi push notification
    private String status = Constants.STATUS_ACTIVE; // "active" hoặc "blocked"

    @ServerTimestamp
    private Timestamp createdAt;
    @ServerTimestamp
    private Timestamp updatedAt;

    // --- Bắt buộc có no-arg constructor cho Firestore ---
    public User() {}

    // --- Constructor tiện ích dùng khi đăng ký ---
    public User(String uid, String email, String displayName,
                String phoneNumber, String role) {
        this.uid = uid;
        this.email = email;
        this.displayName = displayName;
        this.phoneNumber = phoneNumber;
        this.role = role;
        this.avatarUrl = "";
        this.fcmToken = "";
        this.status = Constants.STATUS_ACTIVE;
    }

    // --- Getters & Setters ---
    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getFcmToken() { return fcmToken; }
    public void setFcmToken(String fcmToken) { this.fcmToken = fcmToken; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }
}
