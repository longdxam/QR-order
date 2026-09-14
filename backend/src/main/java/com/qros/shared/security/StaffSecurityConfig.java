package com.qros.shared.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

/**
 * Vùng nhân viên: {@code /api/v1/staff/**} ({@code FR-AUTH-01}, {@code NFR-SEC-05}).
 *
 * <p>Khác vùng khách ở hai điểm, và cả hai bắt nguồn từ một quyết định: token nhân viên nằm trong
 * cookie {@code HttpOnly} chứ không trong {@code localStorage}. Vì trình duyệt tự đính cookie vào
 * mọi request — kể cả request do trang khác kích hoạt — vùng này bắt buộc bật CSRF.
 *
 * <p>Token đọc bằng {@link CookieBearerTokenResolver}, và bộ xác minh là {@code staffJwtVerifier}:
 * khoá, {@code issuer} và {@code audience} đều khác vùng khách.
 */
// Chuỗi filter chỉ có nghĩa trong ứng dụng web; job theo lịch và test không có web context
// vẫn phải khởi động được.
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Configuration(proxyBeanMethods = false)
public class StaffSecurityConfig {

    @Bean
    @Order(2)
    public SecurityFilterChain staffSecurityFilterChain(HttpSecurity http,
            JwtVerifier staffJwtVerifier, SecurityZoneProperties properties,
            ProblemAuthenticationEntryPoint entryPoint,
            ProblemAccessDeniedHandler accessDeniedHandler,
            MfaEnforcementFilter mfaEnforcementFilter,
            TokenVersionValidator tokenVersionValidator) throws Exception {

        CsrfTokenRequestAttributeHandler csrfHandler = new CsrfTokenRequestAttributeHandler();
        // Tắt nạp trễ: nếu không, cookie XSRF-TOKEN chỉ xuất hiện khi có gì đó đọc tới token,
        // và SPA sẽ không bao giờ có token để gửi kèm ở request ghi đầu tiên.
        csrfHandler.setCsrfRequestAttributeName(null);

        CookieSessionAuthenticationFilter cookieFilter =
                new CookieSessionAuthenticationFilter(staffJwtVerifier, entryPoint, tokenVersionValidator);

        return http.securityMatcher("/api/v1/staff/**")
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().hasAuthority("SCOPE_" + properties.staff().scope()))
                .addFilterBefore(cookieFilter, AuthorizationFilter.class)
                // FR-AUTH-02: sau khi biết danh tính (đọc claim từ token) nhưng trước khi phân
                // quyền theo scope — chặn ghi sớm nhất có thể mà vẫn biết được ai đang gọi.
                .addFilterAfter(mfaEnforcementFilter, CookieSessionAuthenticationFilter.class)
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .csrf(csrf -> csrf
                        // Cookie CSRF đọc được bằng JavaScript là cố ý: đây là nửa double submit,
                        // còn token phiên vẫn nằm trong cookie HttpOnly riêng.
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(csrfHandler))
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(cache -> cache.disable())
                .anonymous(anonymous -> anonymous.disable())
                .build();
    }
}
