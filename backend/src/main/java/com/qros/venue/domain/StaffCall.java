package com.qros.venue.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.qros.shared.id.UuidV7;

/** {@code FR-CUS-12}: khách gọi nhân viên, giới hạn 1 lần / 90 giây / bàn. */
@Entity
@Table(name = "staff_call")
public class StaffCall {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "reason", nullable = false)
    private String reason;

    @Column(name = "note")
    private String note;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected StaffCall() {
        // JPA
    }

    public static StaffCall moi(UUID sessionId, String reason, String note, Instant now) {
        StaffCall call = new StaffCall();
        call.id = UuidV7.generate();
        call.sessionId = sessionId;
        call.reason = reason;
        call.note = note;
        call.createdAt = now;
        return call;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
