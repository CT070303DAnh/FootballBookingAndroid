package com.example.footballbooking.data.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.ServerTimestamp;
import java.util.ArrayList;
import java.util.List;

/** Model ánh xạ với Firestore collection "employees". */
public class Employee {

    private String employeeId;
    private String userId;              // Link tới users (nếu có tài khoản)
    private String fullName;
    private String phoneNumber;
    private String email;
    private String position;            // "Bảo vệ", "Phục vụ", "Kỹ thuật"
    private double baseSalary;          // VND/tháng
    private List<String> assignedPitchIds; // Danh sách sân phụ trách
    private String status;              // "active", "inactive"

    @ServerTimestamp
    private Timestamp joinDate;
    @ServerTimestamp
    private Timestamp createdAt;

    public Employee() {
        this.assignedPitchIds = new ArrayList<>();
    }

    // --- Getters & Setters ---
    public String getEmployeeId() { return employeeId; }
    public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPosition() { return position; }
    public void setPosition(String position) { this.position = position; }

    public double getBaseSalary() { return baseSalary; }
    public void setBaseSalary(double baseSalary) { this.baseSalary = baseSalary; }

    public List<String> getAssignedPitchIds() { return assignedPitchIds; }
    public void setAssignedPitchIds(List<String> assignedPitchIds) { this.assignedPitchIds = assignedPitchIds; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Timestamp getJoinDate() { return joinDate; }
    public void setJoinDate(Timestamp joinDate) { this.joinDate = joinDate; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public boolean isActive() {
        return "active".equals(status);
    }
}
