package com.qros.venue.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * Một thiết bị đã tham gia một phiên bàn. {@code deviceId} do client sinh và tự lưu — **không**
 * phải dữ liệu cá nhân (chú thích gốc ở {@code V1__baseline.sql}).
 */
@Entity
@Table(name = "session_device")
@IdClass(SessionDeviceId.class)
public class SessionDevice {

    @Id
    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Id
    @Column(name = "device_id", nullable = false)
    private UUID deviceId;

    @Column(name = "nickname")
    private String nickname;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    protected SessionDevice() {
        // JPA
    }

    public SessionDevice(UUID sessionId, UUID deviceId, String nickname, Instant joinedAt) {
        this.sessionId = sessionId;
        this.deviceId = deviceId;
        this.nickname = nickname;
        this.joinedAt = joinedAt;
    }

    public UUID getDeviceId() {
        return deviceId;
    }

    public String getNickname() {
        return nickname;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }
}
