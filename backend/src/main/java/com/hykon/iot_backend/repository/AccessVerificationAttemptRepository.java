package com.hykon.iot_backend.repository;

import com.hykon.iot_backend.entity.AccessVerificationAttempt;
import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AccessVerificationAttemptRepository
        extends JpaRepository<AccessVerificationAttempt, Long> {

    @Modifying
    @Query(value = """
            INSERT INTO public.access_verification_attempts
                (user_id, failed_attempts, updated_at)
            VALUES (:userId, 0, LOCALTIMESTAMP)
            ON CONFLICT (user_id) DO NOTHING
            """, nativeQuery = true)
    int initializeIfMissing(@Param("userId") Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT a
            FROM AccessVerificationAttempt a
            WHERE a.userId = :userId
            """)
    Optional<AccessVerificationAttempt> findByUserIdForUpdate(
            @Param("userId") Long userId
    );
}