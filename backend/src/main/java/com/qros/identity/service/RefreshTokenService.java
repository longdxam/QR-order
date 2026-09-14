package com.qros.identity.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.qros.identity.domain.StaffRefreshToken;
import com.qros.identity.repository.StaffRefreshTokenRepository;
import com.qros.shared.error.ErrorCode;
import com.qros.shared.error.QrosException;
import com.qros.shared.security.OpaqueTokenHashes;

/**
 * Xoay vòng refresh token có phát hiện tái sử dụng — {@code TM-AUTH-03}, {@code security/RefreshTokenReuseTest}.
 *
 * <p>Thời hạn 7 ngày là lựa chọn của thẻ này, PRD/openapi không chốt con số — dễ đổi sau, chỉ là
 * một hằng số ở đây.
 */
@Service
@ConditionalOnProperty(name = "spring.datasource.url")
public class RefreshTokenService {

    static final Duration TTL = Duration.ofDays(7);

    private final StaffRefreshTokenRepository repository;
    private final Clock clock;

    public RefreshTokenService(StaffRefreshTokenRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public IssuedRefreshToken issueNewFamily(UUID userId) {
        Instant expiresAt = Instant.now(clock).plus(TTL);
        String raw = OpaqueTokenHashes.randomToken();
        StaffRefreshToken entity = StaffRefreshToken.issueNewFamily(
                userId, OpaqueTokenHashes.sha256Hex(raw), expiresAt);
        repository.save(entity);
        return new IssuedRefreshToken(raw, expiresAt, entity.getFamilyId(), userId);
    }

    /**
     * Xoay token: thất bại theo hai cách khác nhau có chủ ý.
     * <ul>
     *   <li>Không tìm thấy hash, hoặc token hết hạn tự nhiên — {@code UNAUTHENTICATED}, không có
     *       gì bất thường, chỉ là phiên đã kết thúc;</li>
     *   <li>Token đã dùng hoặc đã thu hồi trước đó (kể cả thua trong đua tranh song song ở bước
     *       {@link StaffRefreshTokenRepository#claim}) — {@code REFRESH_REUSED} và thu hồi toàn
     *       bộ {@code familyId}: đây là tín hiệu ai đó đang cầm một bản sao token cũ.</li>
     * </ul>
     */
    // noRollbackFor có chủ ý — cùng bẫy đã gặp ở AuthenticationService.login: khi phát hiện tái sử
    // dụng, reuseDetected() ghi revoked_at cho cả family RỒI MỚI ném QrosException. Rollback mặc
    // định của Spring trên RuntimeException sẽ xoá luôn lượt thu hồi đó, khiến "thu hồi cả chuỗi"
    // không có tác dụng gì — token còn lại trong family vẫn dùng được bình thường.
    @Transactional(noRollbackFor = QrosException.class)
    public IssuedRefreshToken rotate(String rawToken) {
        String hash = OpaqueTokenHashes.sha256Hex(rawToken);
        StaffRefreshToken existing = repository.findByTokenHash(hash)
                .orElseThrow(() -> new QrosException(ErrorCode.UNAUTHENTICATED));

        Instant now = Instant.now(clock);
        if (existing.isUsed() || existing.isRevoked()) {
            throw reuseDetected(existing, now);
        }
        if (existing.isExpired(now)) {
            throw new QrosException(ErrorCode.UNAUTHENTICATED);
        }

        int daGianhDuoc = repository.claim(existing.getId(), now);
        if (daGianhDuoc == 0) {
            // Thua trong đua tranh song song: một yêu cầu khác vừa tiêu token này trước.
            throw reuseDetected(existing, now);
        }

        String newRaw = OpaqueTokenHashes.randomToken();
        Instant newExpiresAt = now.plus(TTL);
        StaffRefreshToken next = StaffRefreshToken.issueInFamily(existing.getUserId(),
                existing.getFamilyId(), OpaqueTokenHashes.sha256Hex(newRaw), newExpiresAt);
        repository.save(next);
        return new IssuedRefreshToken(newRaw, newExpiresAt, existing.getFamilyId(), existing.getUserId());
    }

    private QrosException reuseDetected(StaffRefreshToken token, Instant now) {
        repository.revokeFamily(token.getFamilyId(), now);
        return new QrosException(ErrorCode.REFRESH_REUSED);
    }
}
