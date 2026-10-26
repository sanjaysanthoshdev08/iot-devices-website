package com.hykon.iot_backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "devices")
public class Device {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_code", nullable = false, length = 100)
    private String deviceCode;

    @Column(name = "serial_number", nullable = false, length = 150)
    private String serialNumber;

    @Column(name = "system_name", nullable = false, length = 150)
    private String systemName;

    @Column(length = 100)
    private String model;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "branch_id",
        foreignKey = @ForeignKey(name = "fk_devices_branch")
    )
    private DistributorBranch branch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "owner_user_id",
        foreignKey = @ForeignKey(name = "fk_devices_owner")
    )
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "created_by_user_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_devices_creator")
    )
    private User createdBy;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public Device() {
    }

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (status == null || status.isBlank()) {
            status = "ACTIVE";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }

    public String getDeviceCode() { return deviceCode; }
    public void setDeviceCode(String deviceCode) {
        this.deviceCode = deviceCode;
    }

    public String getSerialNumber() { return serialNumber; }
    public void setSerialNumber(String serialNumber) {
        this.serialNumber = serialNumber;
    }

    public String getSystemName() { return systemName; }
    public void setSystemName(String systemName) {
        this.systemName = systemName;
    }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public DistributorBranch getBranch() { return branch; }
    public void setBranch(DistributorBranch branch) {
        this.branch = branch;
    }

    public User getOwner() { return owner; }
    public void setOwner(User owner) { this.owner = owner; }

    public User getCreatedBy() { return createdBy; }
    public void setCreatedBy(User createdBy) {
        this.createdBy = createdBy;
    }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}