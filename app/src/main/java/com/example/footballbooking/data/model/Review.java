package com.example.footballbooking.data.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.ServerTimestamp;
import java.util.ArrayList;
import java.util.List;

/** Model ánh xạ với Firestore collection "reviews". */
public class Review {

    @DocumentId
    private String reviewId;
    private String bookingId;       // Mỗi booking chỉ review được 1 lần
    private String customerId;
    private String customerName;    // Denormalized
    private String customerAvatar;  // Denormalized
    private String pitchId;
    private float rating;           // 1.0 - 5.0
    private String comment;
    private List<String> imageUrls; // Ảnh đính kèm review

    @ServerTimestamp
    private Timestamp createdAt;

    public Review() {
        this.imageUrls = new ArrayList<>();
    }

    // --- Getters & Setters ---
    public String getReviewId() { return reviewId; }
    public void setReviewId(String reviewId) { this.reviewId = reviewId; }

    public String getBookingId() { return bookingId; }
    public void setBookingId(String bookingId) { this.bookingId = bookingId; }

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public String getCustomerAvatar() { return customerAvatar; }
    public void setCustomerAvatar(String customerAvatar) { this.customerAvatar = customerAvatar; }

    public String getPitchId() { return pitchId; }
    public void setPitchId(String pitchId) { this.pitchId = pitchId; }

    public float getRating() { return rating; }
    public void setRating(float rating) { this.rating = rating; }

    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }

    public List<String> getImageUrls() { return imageUrls; }
    public void setImageUrls(List<String> imageUrls) { this.imageUrls = imageUrls; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
}
