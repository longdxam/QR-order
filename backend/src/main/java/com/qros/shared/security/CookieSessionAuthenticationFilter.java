package com.qros.shared.security;

import java.io.IOException;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.AuthenticationEntryPoint;

import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Xác thực phiên nhân viên và quản trị từ cookie {@code qros_session}.
 *
 * <p>Không dùng {@code oauth2ResourceServer} cho hai vùng này, và lý do là bảo mật chứ không phải
 * khẩu vị. Bộ cấu hình resource server của Spring Security **tự động miễn CSRF cho mọi request mà
 * nó nhận ra có bearer token**, dựa trên giả định token nằm ở header {@code Authorization} — nơi
 * một trang lạ không chèn vào được. Ở đây token nằm trong cookie theo {@code NFR-SEC-05}, thứ trình
 * duyệt tự gửi kèm, nên miễn trừ đó vô hiệu hoá đúng lớp phòng thủ mà vùng này cần nhất. Miễn trừ
 * được cộng dồn bằng {@code AND} ở tầng cấu hình nên không gỡ ra được từ DSL; cách sạch là không
 * khai báo hai vùng này là resource server.
 *
 * <p>Đổi lại là một filter tường minh, đọc được từ trên xuống: lấy cookie, xác minh, đặt danh tính,
 * đi tiếp. Thất bại thì trả {@code 401} theo RFC 7807 và **không** để lộ lý do.
 */
public class CookieSessionAuthenticationFilter extends OncePerRequestFilter {

    /** Khoá MDC; {@code logback-spring.xml} đọc đúng khoá này — {@code NFR-OBS-03}. */
    public static final String ACTOR_ID_MDC_KEY = "actorId";

    private final CookieBearerTokenResolver tokenResolver = new CookieBearerTokenResolver();
    private final JwtAuthenticationConverter authenticationConverter = new JwtAuthenticationConverter();

    private final JwtVerifier jwtVerifier;
    private final AuthenticationEntryPoint entryPoint;
    private final TokenVersionValidator tokenVersionValidator;

    public CookieSessionAuthenticationFilter(JwtVerifier jwtVerifier, AuthenticationEntryPoint entryPoint,
            TokenVersionValidator tokenVersionValidator) {
        this.jwtVerifier = jwtVerifier;
        this.entryPoint = entryPoint;
        this.tokenVersionValidator = tokenVersionValidator;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String token = tokenResolver.resolve(request);
        if (token == null) {
            // Không có cookie thì để tầng phân quyền quyết định; endpoint công khai vẫn đi tiếp được.
            filterChain.doFilter(request, response);
            return;
        }

        AbstractAuthenticationToken authentication;
        try {
            Jwt jwt = jwtVerifier.decode(token);
            requireCurrentTokenVersion(jwt);
            authentication = authenticationConverter.convert(jwt);
        } catch (JwtException exception) {
            SecurityContextHolder.clearContext();
            entryPoint.commence(request, response,
                    new InvalidBearerTokenException("Token phiên không hợp lệ", exception));
            return;
        }

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        // NFR-OBS-03: mọi dòng log của request đã xác thực phải mang actorId.
        MDC.put(ACTOR_ID_MDC_KEY, authentication.getName());
        try {
            filterChain.doFilter(request, response);
        } finally {
            // Không lưu vào session: mỗi request tự chứng minh danh tính của nó.
            SecurityContextHolder.clearContext();
            // Thread pool dùng lại luồng — sót MDC là gán nhầm actorId cho request kế tiếp,
            // cùng lý do CorrelationIdFilter đã tự dọn.
            MDC.remove(ACTOR_ID_MDC_KEY);
        }
    }

    /**
     * {@code FR-AUTH-05}: chữ ký và hạn hợp lệ không đủ — {@code tv} trong token phải khớp
     * {@code token_version} hiện tại của tài khoản, nếu không đổi mật khẩu/vai trò/thu hồi quyền sẽ
     * không có tác dụng cho tới khi token cũ tự hết hạn.
     */
    private void requireCurrentTokenVersion(Jwt jwt) {
        // Claim số nguyên qua JSON không đảm bảo là Integer — bộ giải JSON phía JwtVerifier trả
        // Long cho số nguyên JSON, nên ép kiểu thẳng sang Integer ném ClassCastException.
        Number tokenVersion = jwt.getClaim("tv");
        int hienTai = tokenVersion != null ? tokenVersion.intValue() : 0;
        if (!tokenVersionValidator.isCurrent(jwt.getSubject(), hienTai)) {
            throw new BadJwtException("Token mang token_version đã bị vô hiệu hoá");
        }
    }
}
