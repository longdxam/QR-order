package com.qros.identity.controller;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.qros.generated.api.AuthApi;
import com.qros.generated.model.StaffLoginRequest;
import com.qros.identity.service.AuthenticationService;
import com.qros.identity.service.StaffSession;
import com.qros.shared.error.ErrorCode;
import com.qros.shared.error.QrosException;
import com.qros.shared.security.CookieBearerTokenResolver;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

/**
 * {@code POST /api/v1/auth/login} và {@code POST /api/v1/auth/refresh} — {@code openapi.yaml},
 * nhóm {@code auth}.
 *
 * <p>{@code refreshToken()} không nhận tham số vì hợp đồng khai báo thế ({@code AuthApi} sinh từ
 * {@code openapi.yaml}); token đọc trực tiếp từ cookie {@code qros_refresh} qua
 * {@link HttpServletRequest} — Spring tiêm một proxy theo request hiện tại vào field cấp bean đơn,
 * không cần tham số phương thức.
 *
 * <p>Chỉ nạp khi có {@code DataSource}, cùng điều kiện với {@link AuthenticationService} mà nó
 * phụ thuộc. Cũng chỉ nạp trong ứng dụng web: {@code HttpServletRequest} tiêm ở constructor chỉ
 * tồn tại khi có ngữ cảnh servlet — test tích hợp {@code webEnvironment = NONE} (ví dụ
 * {@code integration/IdempotencyTest}) vẫn có {@code DataSource} nhưng không có web context, cùng
 * lý do các {@code *SecurityConfig} đã có sẵn điều kiện này.
 */
@RestController
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(name = "spring.datasource.url")
public class AuthController implements AuthApi {

    private final AuthenticationService authenticationService;
    private final HttpServletRequest request;

    public AuthController(AuthenticationService authenticationService, HttpServletRequest request) {
        this.authenticationService = authenticationService;
        this.request = request;
    }

    @Override
    public ResponseEntity<Void> staffLogin(StaffLoginRequest staffLoginRequest) {
        StaffSession session = authenticationService.login(
                staffLoginRequest.getEmail(), staffLoginRequest.getPassword(), staffLoginRequest.getTotp());
        return noContentWithSessionCookies(session);
    }

    @Override
    public ResponseEntity<Void> refreshToken() {
        String rawRefreshToken = refreshTokenFromCookie()
                .orElseThrow(() -> new QrosException(ErrorCode.UNAUTHENTICATED));
        StaffSession session = authenticationService.refresh(rawRefreshToken);
        return noContentWithSessionCookies(session);
    }

    private ResponseEntity<Void> noContentWithSessionCookies(StaffSession session) {
        ResponseCookie accessCookie = ResponseCookie.from(
                        CookieBearerTokenResolver.COOKIE_NAME, session.accessToken())
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/api")
                .maxAge(session.accessTtl())
                .build();

        Duration refreshTtl = session.refreshToken().ttlFrom(Instant.now());
        ResponseCookie refreshCookie = ResponseCookie.from(
                        CookieBearerTokenResolver.REFRESH_COOKIE_NAME, session.refreshToken().value())
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/api/v1/auth")
                .maxAge(refreshTtl.isNegative() ? Duration.ZERO : refreshTtl)
                .build();

        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, accessCookie.toString())
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .build();
    }

    private Optional<String> refreshTokenFromCookie() {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        for (Cookie cookie : cookies) {
            if (CookieBearerTokenResolver.REFRESH_COOKIE_NAME.equals(cookie.getName())) {
                return Optional.of(cookie.getValue());
            }
        }
        return Optional.empty();
    }
}
