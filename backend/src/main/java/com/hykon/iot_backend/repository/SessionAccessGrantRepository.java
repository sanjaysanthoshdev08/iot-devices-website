package com.hykon.iot_backend.repository;

import com.hykon.iot_backend.entity.SessionAccessGrant;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SessionAccessGrantRepository
        extends JpaRepository<SessionAccessGrant, UUID> {
}