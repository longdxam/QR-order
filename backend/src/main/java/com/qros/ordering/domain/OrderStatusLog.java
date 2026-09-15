package com.qros.ordering.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Nhật ký chuyển trạng thái, chỉ ghi thêm — bằng chứng chống chối bỏ, mục 5.3.1 (Repudiation). */
@Entity
@Table(name = "order_status_log")
public class OrderStatusLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "order_line_id")
    private UUID orderLineId;

    @Column(name = "from_status")
    private String fromStatus;

    @Column(name = "to_status", nullable = false)
    private String toStatus;

    @Column(name = "reason")
    private String reason;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "device_id")
    private UUID deviceId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected OrderStatusLog() {
        // JPA
    }

    public static OrderStatusLog chuyen(UUID orderId, String fromStatus, String toStatus, String reason,
            Instant now) {
        OrderStatusLog log = new OrderStatusLog();
        log.orderId = orderId;
        log.fromStatus = fromStatus;
        log.toStatus = toStatus;
        log.reason = reason;
        log.occurredAt = now;
        return log;
    }

    public static OrderStatusLog chuyenDong(UUID orderId, UUID orderLineId, String fromStatus,
            String toStatus, String reason, UUID actorId, UUID deviceId, Instant now) {
        OrderStatusLog log = chuyen(orderId, fromStatus, toStatus, reason, now);
        log.orderLineId = orderLineId;
        log.actorId = actorId;
        log.deviceId = deviceId;
        return log;
    }
}
