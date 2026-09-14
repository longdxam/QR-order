package com.qros.shared.security;

import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Đọc access token của nhân viên từ cookie {@code qros_session}, không đọc từ header
 * {@code Authorization}.
 *
 * <p>{@code NFR-SEC-05} cấm để token nhân viên trong {@code localStorage} vì XSS đọc được ngay;
 * hệ quả là token nằm trong cookie {@code HttpOnly}, và hệ quả tiếp theo là vùng nhân viên **phải**
 * bật CSRF. Không nhận token từ header ở vùng này là có chủ ý: nhận cả hai đường thì một trang bị
 * XSS có thể tự đính token vào header và đi vòng qua lớp chống CSRF.
 */
public class CookieBearerTokenResolver implements BearerTokenResolver {

    /** Tên cookie do {@code openapi.yaml} quy định ở {@code securitySchemes.staffSession}. */
    public static final String COOKIE_NAME = "qros_session";

    /**
     * Cookie refresh token ({@code TM-AUTH-03}, {@code BL-M0-09}). {@code Path} hẹp hơn — chỉ
     * {@code /api/v1/auth} — nên trình duyệt không đính kèm nó vào mọi request như
     * {@link #COOKIE_NAME}, giảm bề mặt lộ nếu có XSS ở nơi khác trong vùng staff.
     */
    public static final String REFRESH_COOKIE_NAME = "qros_refresh";

    @Override
    public String resolve(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (COOKIE_NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
