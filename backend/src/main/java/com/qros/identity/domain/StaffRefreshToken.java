package com.qros.identity.domain;

import java.time.Instant;
import java.util.UUID;

import com.qros.shared.id.UuidV7;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Một refresh token đã phát ({@code TM-AUTH-03}, {@code V3__mfa_refresh_shift.sql}).
 *
 * <p>Chuỗi ngẫu nhiên đối chiếu CSDL, **không phải** JWT: JWT ký sẵn không thu hồi được trước khi
 * hết hạn, mà xoay vòng có phát hiện tái sử dụng đòi hỏi thu hồi ngay lập tức khi có dấu hiệu bất
 * thường. Chỉ lưu hash SHA-256 của token, không lưu giá trị thật — SHA-256 chứ không phải Argon2id
 * vì đây là chuỗi ngẫu nhiên entropy cao do máy sinh, không phải mật khẩu người chọn ({@code FR-AUTH-01}
 * chỉ bắt buộc Argon2id cho mật khẩu).
 *
 * <p>Mọi token cùng một lượt đăng nhập chia sẻ {@code familyId}. Phát hiện dùng lại một token đã
 * tiêu (đã {@link #isUsed()} hoặc {@link #isRevoked()}) nghĩa là kẻ tấn công có bản sao token cũ —
 * thu hồi toàn bộ {@code familyId}, không chỉ token đó.
 */
@Entity
@Table(name = "refresh_token")
public class StaffRefreshToken {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "family_id", nullable = false)
    private UUID familyId;

    @Column(name = "token_hash", nullable = false)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected StaffRefreshToken() {
        // JPA
    }

    private StaffRefreshToken(UUID id, UUID userId, UUID familyId, String tokenHash, Instant expiresAt) {
        this.id = id;
        this.userId = userId;
        this.familyId = familyId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    /** Bắt đầu một chuỗi token mới — dùng lúc đăng nhập, chưa có {@code familyId} nào trước đó. */
    public static StaffRefreshToken issueNewFamily(UUID userId, String tokenHash, Instant expiresAt) {
        return new StaffRefreshToken(UuidV7.generate(), userId, UuidV7.generate(), tokenHash, expiresAt);
    }

    /** Xoay vòng: token kế tiếp trong cùng một chuỗi đã có — {@code TM-AUTH-03}. */
    public static StaffRefreshToken issueInFamily(UUID userId, UUID familyId, String tokenHash,
            Instant expiresAt) {
        return new StaffRefreshToken(UuidV7.generate(), userId, familyId, tokenHash, expiresAt);
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getFamilyId() {
        return familyId;
    }

    public boolean isUsed() {
        return usedAt != null;
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }
}
