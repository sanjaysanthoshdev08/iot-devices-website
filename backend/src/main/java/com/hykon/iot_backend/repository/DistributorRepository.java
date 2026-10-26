package com.hykon.iot_backend.repository;

import com.hykon.iot_backend.entity.Distributor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface DistributorRepository
        extends JpaRepository<Distributor, Long> {

    Optional<Distributor> findByAdministrator_Id(Long administratorUserId);

    boolean existsByAdministrator_Id(Long administratorUserId);

    Optional<Distributor> findByIdAndAdministrator_Id(
            Long distributorId,
            Long administratorUserId
    );

    @Query("""
            SELECT d
            FROM Distributor d
            WHERE LOWER(TRIM(d.distributorCode)) = LOWER(TRIM(:code))
            """)
    Optional<Distributor> findByCode(@Param("code") String code);
}