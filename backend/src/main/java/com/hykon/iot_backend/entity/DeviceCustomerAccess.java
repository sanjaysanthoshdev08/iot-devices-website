package com.hykon.iot_backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "device_authorized_customers",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_device_authorized_customers_device_user",
        columnNames = {"device_id", "user_id"}
    )
)
public class DeviceCustomerAccess {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "device_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_dev_auth_cust_device")
    )
    private Device device;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "user_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_dev_auth_cust_user")
    )
    private User customer;

    @Column(name = "is_primary_owner", nullable = false)
    private boolean primaryOwner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "created_by_user_id",
        foreignKey = @ForeignKey(name = "fk_dev_auth_cust_creator")
    )
    private User createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public DeviceCustomerAccess() {
    }

    public DeviceCustomerAccess(Device device, User customer, boolean primaryOwner, User createdBy) {
        this.device = device;
        this.customer = customer;
        this.primaryOwner = primaryOwner;
        this.createdBy = createdBy;
        this.createdAt = LocalDateTime.now();
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Long getId() { return id; }

    public Device getDevice() { return device; }
    public void setDevice(Device device) { this.device = device; }

    public User getCustomer() { return customer; }
    public void setCustomer(User customer) { this.customer = customer; }

    public boolean isPrimaryOwner() { return primaryOwner; }
    public void setPrimaryOwner(boolean primaryOwner) { this.primaryOwner = primaryOwner; }

    public User getCreatedBy() { return createdBy; }
    public void setCreatedBy(User createdBy) { this.createdBy = createdBy; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}

