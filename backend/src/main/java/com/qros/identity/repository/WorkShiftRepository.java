package com.qros.identity.repository;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.qros.identity.domain.WorkShift;

public interface WorkShiftRepository extends JpaRepository<WorkShift, UUID> {

    /** @return số ca vừa bị đóng — {@code FR-AUTH-04}, "sau 12 giờ". */
    @Modifying
    @Query("UPDATE WorkShift w SET w.closedAt = :now WHERE w.closedAt IS NULL AND w.openedAt < :threshold")
    int closeExpired(@Param("now") Instant now, @Param("threshold") Instant threshold);
}
