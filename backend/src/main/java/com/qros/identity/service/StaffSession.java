package com.qros.identity.service;

import java.time.Duration;

/**
 * Kết quả đăng nhập/refresh thành công: access token đã ký và refresh token đi kèm, để controller
 * đặt cả hai cookie ({@code qros_session}, {@code qros_refresh}) đúng {@code Max-Age}
 * ({@code NFR-SEC-05}, {@code TM-AUTH-03}).
 */
public record StaffSession(String accessToken, Duration accessTtl, IssuedRefreshToken refreshToken) {
}
