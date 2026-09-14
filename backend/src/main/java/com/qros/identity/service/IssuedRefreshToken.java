package com.qros.identity.service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Giá trị gốc của một refresh token vừa phát — chỉ tồn tại trong bộ nhớ ở đúng lượt gọi này,
 * CSDL chỉ giữ hash ({@code TM-AUTH-03}).
 */
public record IssuedRefreshToken(String value, Instant expiresAt, UUID familyId, UUID userId) {

    public Duration ttlFrom(Instant now) {
        return Duration.between(now, expiresAt);
    }
}
