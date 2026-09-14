package com.qros.identity.domain;

import java.time.Instant;
import java.util.UUID;

import com.qros.shared.id.UuidV7;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Một mã dự phòng MFA dùng một lần ({@code FR-AUTH-02}, {@code V3__mfa_refresh_shift.sql}).
 * Chỉ lưu hash SHA-256 — mã thật chỉ hiện ra đúng một lần lúc phát, không đọc lại được sau đó,
 * cùng nguyên tắc với {@code password_hash}.
 */
@Entity
@Table(name = "mfa_backup_code")
public class MfaBackupCode {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "code_hash", nullable = false)
    private String codeHash;

    @Column(name = "used_at")
    private Instant usedAt;

    protected MfaBackupCode() {
        // JPA
    }

    private MfaBackupCode(UUID id, UUID userId, String codeHash) {
        this.id = id;
        this.userId = userId;
        this.codeHash = codeHash;
    }

    public static MfaBackupCode issue(UUID userId, String codeHash) {
        return new MfaBackupCode(UuidV7.generate(), userId, codeHash);
    }

    public boolean isUsed() {
        return usedAt != null;
    }
}
