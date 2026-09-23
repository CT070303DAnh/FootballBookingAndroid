package com.example.footballbooking.data.model;

/** Model ánh xạ với Firestore collection "services". */
public class Service {

    private String serviceId;
    private String name;         // "Nước uống", "Áo thi đấu", "Trọng tài"
    private String description;
    private double price;        // VND
    private String unit;         // "bình", "bộ", "người"
    private String iconUrl;
    private boolean isActive;

    public Service() {}

    public Service(String serviceId, String name, double price,
                   String description, String unit, boolean isActive) {
        this.serviceId = serviceId;
        this.name = name;
        this.price = price;
        this.description = description;
        this.unit = unit;
        this.isActive = isActive;
    }

    // --- Getters & Setters ---
    public String getServiceId() { return serviceId; }
    public void setServiceId(String serviceId) { this.serviceId = serviceId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public String getIconUrl() { return iconUrl; }
    public void setIconUrl(String iconUrl) { this.iconUrl = iconUrl; }

    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }
}
