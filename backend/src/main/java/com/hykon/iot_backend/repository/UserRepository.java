
package com.hykon.iot_backend.repository;

import com.hykon.iot_backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    @Query("""
            SELECT u
            FROM User u
            JOIN FETCH u.role
            WHERE LOWER(u.email) = LOWER(:email)
            """)
    Optional<User> findByEmailWithRole(@Param("email") String email);
}