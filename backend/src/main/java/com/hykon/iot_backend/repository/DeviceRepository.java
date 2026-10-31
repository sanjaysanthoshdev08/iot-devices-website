package com.hykon.iot_backend.repository;

import com.hykon.iot_backend.entity.Device;
import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
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

    @Query("""
            SELECT d
            FROM Device d
            WHERE d.id = :deviceId
              AND (
                d.owner.id = :ownerUserId
                OR EXISTS (
                  SELECT 1 FROM DeviceCustomerAccess dca
                  WHERE dca.device.id = d.id AND dca.customer.id = :ownerUserId
                )
              )
            """)
    Optional<Device> findByIdAndOwner_Id(
            @Param("deviceId") Long deviceId,
            @Param("ownerUserId") Long ownerUserId
    );

    @Query(
            value = """
                    SELECT d
                    FROM Device d
                    WHERE d.owner.id = :ownerUserId
                       OR EXISTS (
                         SELECT 1 FROM DeviceCustomerAccess dca
                         WHERE dca.device.id = d.id AND dca.customer.id = :ownerUserId
                       )
                    """,
            countQuery = """
                    SELECT COUNT(d)
                    FROM Device d
                    WHERE d.owner.id = :ownerUserId
                       OR EXISTS (
                         SELECT 1 FROM DeviceCustomerAccess dca
                         WHERE dca.device.id = d.id AND dca.customer.id = :ownerUserId
                       )
                    """
    )
    Page<Device> findByOwner_Id(
            @Param("ownerUserId") Long ownerUserId,
            Pageable pageable
    );

    @Query(
            value = """
                    SELECT d
                    FROM Device d
                    WHERE d.owner.id = :userId
                       OR EXISTS (
                         SELECT 1 FROM DeviceCustomerAccess dca
                         WHERE dca.device.id = d.id AND dca.customer.id = :userId
                       )
                    """,
            countQuery = """
                    SELECT COUNT(d)
                    FROM Device d
                    WHERE d.owner.id = :userId
                       OR EXISTS (
                         SELECT 1 FROM DeviceCustomerAccess dca
                         WHERE dca.device.id = d.id AND dca.customer.id = :userId
                       )
                    """
    )
    Page<Device> findAuthorizedDevicesForUser(
            @Param("userId") Long userId,
            Pageable pageable
    );

    @Query("""
            SELECT d
            FROM Device d
            WHERE d.id = :deviceId
              AND (
                d.owner.id = :userId
                OR EXISTS (
                  SELECT 1 FROM DeviceCustomerAccess dca
                  WHERE dca.device.id = d.id AND dca.customer.id = :userId
                )
              )
            """)
    Optional<Device> findAuthorizedDeviceByIdAndUser(
            @Param("deviceId") Long deviceId,
            @Param("userId") Long userId
    );

    Optional<Device> findByIdAndBranch_Distributor_Administrator_Id(
            Long deviceId,
            Long administratorUserId
    );

    Page<Device> findByBranch_Distributor_Administrator_Id(
            Long administratorUserId,
            Pageable pageable
    );

    List<Device> findByBranch_IdIn(List<Long> branchIds);

    Page<Device> findByBranch_Id(Long branchId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT d
            FROM Device d
            WHERE LOWER(TRIM(d.deviceCode)) = LOWER(TRIM(:code))
            """)
    Optional<Device> findByCodeForUpdate(@Param("code") String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT d
            FROM Device d
            WHERE d.id = :deviceId
            """)
    Optional<Device> findByIdForUpdate(
            @Param("deviceId") Long deviceId
    );
}
