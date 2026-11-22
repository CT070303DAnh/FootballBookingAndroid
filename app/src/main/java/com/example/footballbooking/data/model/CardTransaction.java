package com.example.footballbooking.data.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;

/**
 * Model ánh xạ với Firestore collection "card_transactions"
 * được đồng bộ từ phần mềm POS thẻ thông minh (NetBeans + jCardSim).
 */
public class CardTransaction {

    @DocumentId
    private String txId;
    private String userId;
    private String cardId;
    private String type;          // "topup", "pay_booking", "pay_service", "redeem_reward"
    private double amount;
    private int pointsDelta;
    private String description;
    private String bookingId;
    private String rsaSignature;
    private Timestamp createdAt;

    public CardTransaction() {}

    public String getTxId() { return txId; }
    public void setTxId(String txId) { this.txId = txId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getCardId() { return cardId; }
    public void setCardId(String cardId) { this.cardId = cardId; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }

    public int getPointsDelta() { return pointsDelta; }
    public void setPointsDelta(int pointsDelta) { this.pointsDelta = pointsDelta; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getBookingId() { return bookingId; }
    public void setBookingId(String bookingId) { this.bookingId = bookingId; }

    public String getRsaSignature() { return rsaSignature; }
    public void setRsaSignature(String rsaSignature) { this.rsaSignature = rsaSignature; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public boolean hasRsaSignature() {
        return rsaSignature != null && !rsaSignature.trim().isEmpty();
    }
}
