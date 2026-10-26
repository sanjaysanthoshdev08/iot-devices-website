package com.hykon.iot_backend.repository;

import com.hykon.iot_backend.entity.DistributorBranch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DistributorBranchRepository
        extends JpaRepository<DistributorBranch, Long> {

    List<DistributorBranch> findByDistributor_Id(Long distributorId);

    Optional<DistributorBranch> findByIdAndDistributor_Id(
            Long branchId,
            Long distributorId
    );

    @Query("""
            SELECT b
            FROM DistributorBranch b
            WHERE b.distributor.id = :distributorId
              AND LOWER(TRIM(b.branchCode)) = LOWER(TRIM(:branchCode))
            """)
    Optional<DistributorBranch> findByDistributorAndCode(
            @Param("distributorId") Long distributorId,
            @Param("branchCode") String branchCode
    );
}