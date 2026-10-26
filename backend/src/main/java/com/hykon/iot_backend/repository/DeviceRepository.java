package com.hykon.iot_backend.repository;

import com.hykon.iot_backend.entity.Device;
import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface DeviceRepository extends JpaRepository<Device, Long> {

    @Query("""
            SELECT d
            FROM Device d
            WHERE LOWER(TRIM(d.deviceCode)) = LOWER(TRIM(:code))
            """)
    Optional<Device> findByCode(@Param("code") String code);

    @Query("""
            SELECT d
            FROM Device d
            WHERE LOWER(TRIM(d.serialNumber)) = LOWER(TRIM(:serialNumber))
            """)
    Optional<Device> findBySerialNumberNormalized(
            @Param("serialNumber") String serialNumber
    );

    Optional<Device> findByIdAndOwner_Id(
            Long deviceId,
            Long ownerUserId
    );

    Page<Device> findByOwner_Id(
            Long ownerUserId,
            Pageable pageable
    );

    Optional<Device> findByIdAndBranch_Distributor_Administrator_Id(
            Long deviceId,
            Long administratorUserId
    );

    Page<Device> findByBranch_Distributor_Administrator_Id(
            Long administratorUserId,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT d
            FROM Device d
            WHERE LOWER(TRIM(d.deviceCode)) = LOWER(TRIM(:code))
            """)
    Optional<Device> findByCodeForUpdate(@Param("code") String code);
}