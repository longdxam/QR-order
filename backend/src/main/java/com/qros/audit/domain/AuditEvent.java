package com.qros.audit.domain;

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
 * Một dòng nhật ký bất biến ({@code audit_event} của {@code V1__baseline.sql}) —
 * {@code TM-REP-01}: "Audit chỉ ghi thêm".
 *
 * <p>Không có setter, không có cách nào đổi một trường sau khi dựng — bất biến ở cấp Java khớp với
 * bất biến ở cấp CSDL: {@link com.qros.audit.repository.AuditEventRepository} không thừa kế
 * {@code JpaRepository}/{@code CrudRepository} nên không có {@code save}/{@code delete} nào để gọi
 * nhầm, và {@code ModuleBoundaryTest} giữ luật đó không bị nới lỏng.
 *
 * <p>Cột {@code ip_address} (kiểu {@code inet}) chưa map — chưa có nơi gọi nào có IP khách thật để
 * ghi (webhook/thao tác quản trị đều thuộc M2/M3); thêm khi có nhu cầu thay vì đoán trước.
 */
@Entity
@Table(name = "audit_event")
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "actor_role")
    private String actorRole;

    @Column(name = "store_id")
    private UUID storeId;

    @Column(name = "action", nullable = false)
    private String action;

    @Column(name = "entity_type", nullable = false)
    private String entityType;

    @Column(name = "entity_id")
    private UUID entityId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "before_value")
    private String beforeValue;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "after_value")
    private String afterValue;

    @Column(name = "reason")
    private String reason;

    @Column(name = "trace_id")
    private String traceId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected AuditEvent() {
        // JPA
    }

    private AuditEvent(UUID actorId, String actorRole, UUID storeId, String action, String entityType,
            UUID entityId, String beforeValue, String afterValue, String reason, String traceId,
            Instant occurredAt) {
        this.actorId = actorId;
        this.actorRole = actorRole;
        this.storeId = storeId;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.beforeValue = beforeValue;
        this.afterValue = afterValue;
        this.reason = reason;
        this.traceId = traceId;
        this.occurredAt = occurredAt;
    }

    /**
     * Duy nhất một cách tạo — không có phương thức nào khác thay đổi trạng thái sau đó.
     * {@code beforeJson}/{@code afterJson} đã tuần tự hoá sẵn: entity không phụ thuộc
     * {@code ObjectMapper}, đó là việc của {@code AuditService}.
     */
    public static AuditEvent record(UUID actorId, String actorRole, UUID storeId, String action,
            String entityType, UUID entityId, String beforeJson, String afterJson, String reason,
            String traceId, Instant occurredAt) {
        return new AuditEvent(actorId, actorRole, storeId, action, entityType, entityId,
                beforeJson, afterJson, reason, traceId, occurredAt);
    }

    public Long getId() {
        return id;
    }

    public UUID getActorId() {
        return actorId;
    }

    public String getActorRole() {
        return actorRole;
    }

    public UUID getStoreId() {
        return storeId;
    }

    public String getAction() {
        return action;
    }

    public String getEntityType() {
        return entityType;
    }

    public UUID getEntityId() {
        return entityId;
    }

    public String getBeforeValue() {
        return beforeValue;
    }

    public String getAfterValue() {
        return afterValue;
    }

    public String getReason() {
        return reason;
    }

    public String getTraceId() {
        return traceId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
