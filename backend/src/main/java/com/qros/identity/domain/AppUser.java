package com.qros.identity.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import com.qros.shared.security.TokenVersionValidator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Tài khoản nhân viên ({@code FR-AUTH-01}). Khoá luỹ tiến của {@code FR-AUTH-03} là hành vi của
 * chính entity này chứ không nằm ở service — cùng lối với {@code Order}/{@code OrderStateMachine}:
 * bất biến của một thực thể sống cùng thực thể đó.
 *
 * <p>Hai khái niệm tách bạch có chủ ý, xem thêm {@code V2__auth_lockout_window.sql}:
 * <ul>
 *   <li><b>cửa sổ dồn lỗi</b> ({@code failedAttempts}/{@code lastFailedAt}) — chỉ đếm các lần sai
 *       liên tiếp trong 15 phút, quy định tại {@code FR-AUTH-03};</li>
 *   <li><b>luỹ tiến</b> ({@code lockoutCount}) — đếm số lần đã bị khoá, không reset theo thời gian,
 *       chỉ về 0 khi đăng nhập thành công. Thời gian khoá là {@code 15 phút × 2^(lockoutCount-1)},
 *       trần {@code 24 giờ}: lần 1 = 15 phút, lần 2 = 30 phút, lần 3 = 60 phút, v.v.</li>
 * </ul>
 */
@Entity
@Table(name = "app_user")
public class AppUser {

    /** Số lần sai liên tiếp trong cửa sổ 15 phút trước khi khoá — {@code FR-AUTH-03}. */
    private static final int MAX_ATTEMPTS_BEFORE_LOCK = 5;
    private static final Duration BURST_WINDOW = Duration.ofMinutes(15);
    private static final Duration BASE_LOCK_DURATION = Duration.ofMinutes(15);
    private static final Duration MAX_LOCK_DURATION = Duration.ofHours(24);

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    // citext ở CSDL (không phân biệt hoa/thường); columnDefinition để ddl-auto=validate không so
    // khớp với varchar mặc định của String.
    @Column(name = "email", columnDefinition = "citext")
    private String email;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "mfa_enabled", nullable = false)
    private boolean mfaEnabled;

    // Lưu trực tiếp secret TOTP (base64), không phải tham chiếu Vault như chú thích gốc ở
    // V1__baseline.sql mô tả — dự án chưa tích hợp Vault/KMS ở đâu cả (khoá ký JWT cũng đang là
    // biến môi trường thô, BL-M0-07), nên tạm nhất quán với hiện trạng đó thay vì giả vờ có KMS.
    @Column(name = "mfa_secret_ref")
    private String mfaSecretRef;

    @Column(name = "token_version", nullable = false)
    private int tokenVersion;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    @Column(name = "last_failed_at")
    private Instant lastFailedAt;

    @Column(name = "lockout_count", nullable = false)
    private int lockoutCount;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "active", nullable = false)
    private boolean active;

    protected AppUser() {
        // JPA
    }

    /**
     * Tạo tài khoản mới. Gói hẹp trong module: quản trị tạo tài khoản qua API
     * ({@code FR-MGT-07}) là M3, chưa có; hiện chỉ test dùng constructor này qua
     * {@code AppUserFixture} nằm cùng package.
     */
    AppUser(UUID id, String email, String passwordHash, String displayName, boolean active) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.active = active;
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isMfaEnabled() {
        return mfaEnabled;
    }

    public String getMfaSecretRef() {
        return mfaSecretRef;
    }

    public int getTokenVersion() {
        return tokenVersion;
    }

    /** Bật MFA sau khi đã xác nhận một mã TOTP hợp lệ — {@code FR-AUTH-02}. */
    public void activateMfa(String secretBase64) {
        this.mfaSecretRef = secretBase64;
        this.mfaEnabled = true;
    }

    /**
     * {@code FR-AUTH-05}: đổi mật khẩu, đổi vai trò, hoặc thu hồi quyền phải gọi hàm này để vô
     * hiệu hoá toàn bộ token đang hoạt động — {@link TokenVersionValidator} phía
     * {@code shared/security} sẽ từ chối mọi token mang giá trị cũ.
     */
    public void bumpTokenVersion() {
        tokenVersion++;
    }

    public boolean isActive() {
        return active;
    }

    public boolean isLocked(Instant now) {
        return lockedUntil != null && now.isBefore(lockedUntil);
    }

    /**
     * Ghi nhận một lần đăng nhập sai. Gọi sau khi đã kiểm {@link #isLocked(Instant)} là
     * {@code false} — hàm này không tự kiểm lại.
     *
     * @return {@code true} nếu lần sai này vừa kích hoạt khoá mới.
     */
    public boolean registerFailedAttempt(Instant now) {
        boolean cungCuaSo = lastFailedAt != null && !now.isAfter(lastFailedAt.plus(BURST_WINDOW));
        failedAttempts = cungCuaSo ? failedAttempts + 1 : 1;
        lastFailedAt = now;

        if (failedAttempts < MAX_ATTEMPTS_BEFORE_LOCK) {
            return false;
        }

        lockoutCount++;
        lockedUntil = now.plus(lockDurationFor(lockoutCount));
        // Đợt dồn lỗi đã "tiêu thụ" thành một lần khoá; đợt kế tiếp bắt đầu lại từ 0 sau khi hết khoá.
        failedAttempts = 0;
        return true;
    }

    /** Đăng nhập thành công xoá sạch lịch sử khoá — kể cả bộ đếm luỹ tiến. */
    public void registerSuccessfulLogin() {
        failedAttempts = 0;
        lastFailedAt = null;
        lockoutCount = 0;
        lockedUntil = null;
    }

    private static Duration lockDurationFor(int lockoutCount) {
        // lockoutCount >= 1 khi hàm này được gọi. Số mũ chỉ cần tới 7 là đã vượt trần 24 giờ
        // (15 phút × 2^7 = 32 giờ); chặn ở 32 để không bao giờ tràn phép dịch bit dù lockoutCount
        // lớn bất thường.
        int soMu = Math.min(lockoutCount - 1, 32);
        Duration scaled = BASE_LOCK_DURATION.multipliedBy(1L << soMu);
        return scaled.compareTo(MAX_LOCK_DURATION) > 0 ? MAX_LOCK_DURATION : scaled;
    }
}
