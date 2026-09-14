package com.qros.shared.idempotency;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Một khoá idempotency đã dùng, kèm kết quả đã trả cho client ({@code FR-CUS-09}).
 *
 * <p>Bản ghi sống 24 giờ. Trong khoảng đó, cùng một khoá phải trả lại **đúng** kết quả cũ chứ
 * không chạy lại tác dụng phụ — đây là lớp chắn giữa "khách chạm hai lần" và "hai đơn"
 * ({@code TM-ORD-02}).
 */
@Entity
@Table(name = "idempotency_key")
public class IdempotencyEntity {

    @Id
    @Column(name = "key", nullable = false)
    private UUID key;

    @Column(name = "scope", nullable = false)
    private String scope;

    @Column(name = "session_id")
    private UUID sessionId;

    /**
     * Vân tay của yêu cầu. Cùng khoá nhưng khác vân tay là dấu hiệu client lỗi: phải báo lỗi,
     * chứ tuyệt đối không trả kết quả của một yêu cầu khác.
     */
    @Column(name = "request_hash", nullable = false)
    private String requestHash;

    @Column(name = "response_status")
    private Integer responseStatus;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_body")
    private String responseBody;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected IdempotencyEntity() {
        // JPA
    }

    public UUID getKey() {
        return key;
    }

    public String getScope() {
        return scope;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public Integer getResponseStatus() {
        return responseStatus;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
