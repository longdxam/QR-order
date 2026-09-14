package com.qros.shared.event;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Một dòng của bảng {@code outbox_event}.
 *
 * <p>Cột {@code id} kiểu {@code bigserial} **chính là** trường {@code seq} trong
 * {@code asyncapi.yaml} (SDD mục 5). Nó cho thứ tự tuyệt đối không phụ thuộc đồng hồ của máy nào,
 * và là mốc để client phát lại sau khi kết nối lại ({@code FR-BAR-06}). Vì vậy không được đổi
 * sang UUID hay sang khoá do ứng dụng sinh.
 */
@Entity
@Table(name = "outbox_event")
public class OutboxEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "aggregate_type", nullable = false)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "store_id")
    private UUID storeId;

    @Column(name = "session_id")
    private UUID sessionId;

    @Column(name = "type", nullable = false)
    private String type;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false)
    private String payload;

    @Column(name = "trace_id")
    private String traceId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    protected OutboxEntity() {
        // JPA
    }

    OutboxEntity(String aggregateType, UUID aggregateId, UUID storeId, UUID sessionId,
            String type, String payload, String traceId, Instant occurredAt) {
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.storeId = storeId;
        this.sessionId = sessionId;
        this.type = type;
        this.payload = payload;
        this.traceId = traceId;
        this.occurredAt = occurredAt;
    }

    /** Cũng chính là {@code seq} của phong bì sự kiện. */
    public Long getId() {
        return id;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public UUID getAggregateId() {
        return aggregateId;
    }

    public UUID getStoreId() {
        return storeId;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public String getType() {
        return type;
    }

    public String getPayload() {
        return payload;
    }

    public String getTraceId() {
        return traceId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }
}
