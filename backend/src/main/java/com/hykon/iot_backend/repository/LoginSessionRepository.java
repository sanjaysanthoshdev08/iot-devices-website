package com.hykon.iot_backend.repository;

import com.hykon.iot_backend.entity.LoginSession;
import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface LoginSessionRepository
        extends JpaRepository<LoginSession, UUID> {

    Optional<LoginSession> findByIdAndUser_Id(
            UUID sessionId,
            Long userId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT s
            FROM LoginSession s
            WHERE s.id = :sessionId
            """)
    Optional<LoginSession> findByIdForUpdate(
            @Param("sessionId") UUID sessionId
    );
}