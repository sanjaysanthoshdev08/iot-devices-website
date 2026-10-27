package com.hykon.iot_backend.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "session_access_grants")
public class SessionAccessGrant {

    @Id
    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "device_id",
            foreignKey = @ForeignKey(name = "fk_access_grants_device")
    )
    private Device device;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "branch_id",
            foreignKey = @ForeignKey(name = "fk_access_grants_branch")
    )
    private DistributorBranch branch;

    @Column(name = "device_verified_at")
    private LocalDateTime deviceVerifiedAt;

    @Column(name = "distributor_verified_at")
    private LocalDateTime distributorVerifiedAt;

    public SessionAccessGrant() {
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public void setSessionId(UUID sessionId) {
        this.sessionId = sessionId;
    }

    public Device getDevice() {
        return device;
    }

    public void setDevice(Device device) {
        this.device = device;
    }

    public DistributorBranch getBranch() {
        return branch;
    }

    public void setBranch(DistributorBranch branch) {
        this.branch = branch;
    }

    public LocalDateTime getDeviceVerifiedAt() {
        return deviceVerifiedAt;
    }

    public void setDeviceVerifiedAt(LocalDateTime deviceVerifiedAt) {
        this.deviceVerifiedAt = deviceVerifiedAt;
    }

    public LocalDateTime getDistributorVerifiedAt() {
        return distributorVerifiedAt;
    }

    public void setDistributorVerifiedAt(
            LocalDateTime distributorVerifiedAt
    ) {
        this.distributorVerifiedAt = distributorVerifiedAt;
    }
}