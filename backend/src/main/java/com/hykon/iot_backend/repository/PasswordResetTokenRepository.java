package com.hykon.iot_backend.repository;

import com.hykon.iot_backend.entity.PasswordResetToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    // Newest unused code for the user (at most one is active at a time).
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT t FROM PasswordResetToken t
            WHERE t.user.id = :userId AND t.used = false
            ORDER BY t.createdAt DESC
            """)
    List<PasswordResetToken> findActiveForUserForUpdate(@Param("userId") Long userId);

    @Modifying
    @Query("UPDATE PasswordResetToken t SET t.used = true WHERE t.user.id = :userId AND t.used = false")
    int invalidateAllForUser(@Param("userId") Long userId);

    // Revoke every login session / refresh token so old logins stop working
    // after a password reset. Only updates rows; no existing code changes.
    @Modifying
    @Query("UPDATE LoginSession s SET s.revoked = true WHERE s.user.id = :userId AND s.revoked = false")
    int revokeLoginSessionsForUser(@Param("userId") Long userId);

    @Modifying
    @Query("UPDATE RefreshToken r SET r.revoked = true WHERE r.user.id = :userId AND r.revoked = false")
    int revokeRefreshTokensForUser(@Param("userId") Long userId);
}
