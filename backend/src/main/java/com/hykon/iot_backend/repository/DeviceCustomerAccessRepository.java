package com.hykon.iot_backend.repository;

import com.hykon.iot_backend.entity.DeviceCustomerAccess;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DeviceCustomerAccessRepository extends JpaRepository<DeviceCustomerAccess, Long> {

    @Query("SELECT dca FROM DeviceCustomerAccess dca JOIN FETCH dca.customer WHERE dca.device.id = :deviceId ORDER BY dca.id ASC")
    List<DeviceCustomerAccess> findByDeviceIdWithCustomer(@Param("deviceId") Long deviceId);

    @Query("SELECT dca FROM DeviceCustomerAccess dca JOIN FETCH dca.customer WHERE dca.device.id IN :deviceIds ORDER BY dca.id ASC")
    List<DeviceCustomerAccess> findByDeviceIdInWithCustomer(@Param("deviceIds") List<Long> deviceIds);

    @Query("SELECT dca FROM DeviceCustomerAccess dca WHERE dca.device.id = :deviceId ORDER BY dca.id ASC")
    List<DeviceCustomerAccess> findByDeviceId(@Param("deviceId") Long deviceId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT dca FROM DeviceCustomerAccess dca WHERE dca.device.id = :deviceId")
    List<DeviceCustomerAccess> findByDeviceIdForUpdate(@Param("deviceId") Long deviceId);

    Optional<DeviceCustomerAccess> findByDeviceIdAndCustomerId(Long deviceId, Long customerId);

    long countByDeviceId(Long deviceId);

    boolean existsByDeviceIdAndCustomerId(Long deviceId, Long customerId);

    @Modifying
    @Query("DELETE FROM DeviceCustomerAccess dca WHERE dca.device.id = :deviceId AND dca.customer.id = :customerId")
    void deleteByDeviceIdAndCustomerId(@Param("deviceId") Long deviceId, @Param("customerId") Long customerId);
}

