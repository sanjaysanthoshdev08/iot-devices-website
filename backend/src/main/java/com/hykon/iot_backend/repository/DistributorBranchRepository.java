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

    org.springframework.data.domain.Page<DistributorBranch> findByDistributor_Id(Long distributorId, org.springframework.data.domain.Pageable pageable);

    List<DistributorBranch> findByDistributor_IdIn(List<Long> distributorIds);

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

    @Query("""
            SELECT b
            FROM DistributorBranch b
            WHERE b.distributor.id = :distributorId
              AND LOWER(TRIM(b.name)) = LOWER(TRIM(:branchName))
              AND LOWER(TRIM(b.location)) = LOWER(TRIM(:location))
            """)
    List<DistributorBranch> findByDistributorAndNameAndLocation(
            @Param("distributorId") Long distributorId,
            @Param("branchName") String branchName,
            @Param("location") String location
    );
}