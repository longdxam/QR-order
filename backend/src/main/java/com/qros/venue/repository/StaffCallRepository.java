package com.qros.venue.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.qros.venue.domain.StaffCall;

public interface StaffCallRepository extends JpaRepository<StaffCall, UUID> {

    Optional<StaffCall> findFirstBySessionIdOrderByCreatedAtDesc(UUID sessionId);
}
