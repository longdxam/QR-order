package com.qros.venue.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.qros.venue.domain.SessionDevice;
import com.qros.venue.domain.SessionDeviceId;

public interface SessionDeviceRepository extends JpaRepository<SessionDevice, SessionDeviceId> {

    List<SessionDevice> findBySessionIdOrderByJoinedAt(UUID sessionId);

    /** {@code EC-02} "nghi ngờ lạm dụng": đếm thiết bị đã tham gia trong 30 phút gần nhất. */
    long countBySessionIdAndJoinedAtAfter(UUID sessionId, Instant since);

    boolean existsBySessionIdAndDeviceId(UUID sessionId, UUID deviceId);
}
