package com.hykon.iot_backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "distributors",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_distributors_administrator",
        columnNames = "administrator_user_id"
    )
)
public class Distributor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "distributor_code", nullable = false, length = 100)
    private String distributorCode;

    @Column(nullable = false, length = 150)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "administrator_user_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_distributors_administrator")
    )
    private User administrator;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "created_by_user_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_distributors_creator")
    )
    private User createdBy;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public Distributor() {
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

    public String getDistributorCode() { return distributorCode; }
    public void setDistributorCode(String distributorCode) {
        this.distributorCode = distributorCode;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public User getAdministrator() { return administrator; }
    public void setAdministrator(User administrator) {
        this.administrator = administrator;
    }

    public User getCreatedBy() { return createdBy; }
    public void setCreatedBy(User createdBy) {
        this.createdBy = createdBy;
    }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}