package com.hykon.iot_backend.repository;

import com.hykon.iot_backend.entity.DevicePurchase;
import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface DevicePurchaseRepository
        extends JpaRepository<DevicePurchase, Long> {

    Optional<DevicePurchase> findByDevice_Id(Long deviceId);

    boolean existsByDevice_Id(Long deviceId);

    Optional<DevicePurchase> findByDevice_IdAndCustomer_Id(
            Long deviceId,
            Long customerUserId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT p
            FROM DevicePurchase p
            WHERE p.device.id = :deviceId
            """)
    Optional<DevicePurchase> findByDeviceIdForUpdate(
            @Param("deviceId") Long deviceId
    );
}