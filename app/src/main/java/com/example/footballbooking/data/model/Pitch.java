package com.example.footballbooking.data.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.GeoPoint;
import com.google.firebase.firestore.ServerTimestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * Model ánh xạ với Firestore collection "pitches".
 * GeoPoint được Firestore hỗ trợ native — dùng cho Maps và AI scoring.
 */
public class Pitch {

    @DocumentId
    private String pitchId;
    private String name;
    private String description;
    private String type;            // "5", "7", "11"
    private String status;          // "available", "maintenance", "closed"
    private String address;
    private GeoPoint location;      // lat, lng — Firestore GeoPoint
    private List<String> imageUrls;
    private List<String> amenities; // ["Đèn cao áp", "Mái che", ...]
    private double basePrice;       // VND/giờ
    private float rating;           // 0.0 - 5.0
    private int totalReviews;
    private String ownerId;
    private String ownerName;       // Denormalized — tránh extra query
    private String ownerPhone;      // SĐT chủ sân hiển thị cho customer

    @ServerTimestamp
    private Timestamp createdAt;
    @ServerTimestamp
    private Timestamp updatedAt;

    // --- No-arg constructor bắt buộc ---
    public Pitch() {
        this.imageUrls = new ArrayList<>();
        this.amenities = new ArrayList<>();
    }

    // --- Getters & Setters ---
    public String getPitchId() { return pitchId; }
    public void setPitchId(String pitchId) { this.pitchId = pitchId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public GeoPoint getLocation() { return location; }
    public void setLocation(GeoPoint location) { this.location = location; }

    public List<String> getImageUrls() { return imageUrls; }
    public void setImageUrls(List<String> imageUrls) { this.imageUrls = imageUrls; }

    public List<String> getAmenities() { return amenities; }
    public void setAmenities(List<String> amenities) { this.amenities = amenities; }

    public double getBasePrice() { return basePrice; }
    public void setBasePrice(double basePrice) { this.basePrice = basePrice; }

    public float getRating() { return rating; }
    public void setRating(float rating) { this.rating = rating; }

    public int getTotalReviews() { return totalReviews; }
    public void setTotalReviews(int totalReviews) { this.totalReviews = totalReviews; }

    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }

    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }

    public String getOwnerPhone() { return ownerPhone; }
    public void setOwnerPhone(String ownerPhone) { this.ownerPhone = ownerPhone; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }

    // --- Utility methods ---
    public boolean isAvailable() {
        return "available".equals(this.status);
    }

    public String getDisplayType() {
        return "Sân " + type + " người";
    }
}
