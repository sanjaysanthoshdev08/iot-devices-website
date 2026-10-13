
package com.hykon.iot_backend.repository;

import com.hykon.iot_backend.entity.RefreshToken;
import com.hykon.iot_backend.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    // Existing method retained.
    Optional<RefreshToken> findByToken(String token);

    // New method: locks the matching row during a transaction.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT rt FROM RefreshToken rt WHERE rt.token = :token")
    Optional<RefreshToken> findByTokenForUpdate(
            @Param("token") String token
    );

    // Existing method retained.
    List<RefreshToken> findAllByUser(User user);

}
