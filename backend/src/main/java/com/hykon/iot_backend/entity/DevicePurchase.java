package com.hykon.iot_backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "device_purchases",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_device_purchases_device",
        columnNames = "device_id"
    )
)
public class DevicePurchase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "device_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_device_purchases_device")
    )
    private Device device;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "customer_user_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_device_purchases_customer")
    )
    private User customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "recorded_by_user_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_device_purchases_recorder")
    )
    private User recordedBy;

    @Column(name = "purchase_reference", nullable = false, length = 150)
    private String purchaseReference;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "claimed_at")
    private LocalDateTime claimedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public DevicePurchase() {
    }

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (status == null || status.isBlank()) {
            status = "ELIGIBLE";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }

    public Device getDevice() { return device; }
    public void setDevice(Device device) { this.device = device; }

    public User getCustomer() { return customer; }
    public void setCustomer(User customer) { this.customer = customer; }

    public User getRecordedBy() { return recordedBy; }
    public void setRecordedBy(User recordedBy) {
        this.recordedBy = recordedBy;
    }

    public String getPurchaseReference() { return purchaseReference; }
    public void setPurchaseReference(String purchaseReference) {
        this.purchaseReference = purchaseReference;
    }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getClaimedAt() { return claimedAt; }
    public void setClaimedAt(LocalDateTime claimedAt) {
        this.claimedAt = claimedAt;
    }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}