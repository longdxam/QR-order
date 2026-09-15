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
 * Vùng quản trị: {@code /api/v1/admin/**} ({@code FR-AUTH-01}, {@code NFR-SEC-05}).
 *
 * <p>Cấu hình trông giống vùng nhân viên nhưng là một chuỗi riêng, với bộ khoá và {@code audience}
 * riêng. Gộp hai vùng lại cho gọn sẽ khiến một token nhân viên bình thường mở được cả API quản trị
 * ngay khi có ai đó nới một dòng phân quyền.
 *
 * <p>Hai lớp nữa thuộc vùng này nhưng chưa nằm ở đây, có chủ ý:
 * <ul>
 *   <li>bắt buộc MFA cho quyền ghi — {@code FR-AUTH-02}, thẻ {@code BL-M0-09};</li>
 *   <li>allowlist IP — còn ở {@code OPEN-05}, chưa chốt là Must hay Should.</li>
 * </ul>
 */
// Chuỗi filter chỉ có nghĩa trong ứng dụng web; job theo lịch và test không có web context
// vẫn phải khởi động được.
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Configuration(proxyBeanMethods = false)
public class AdminSecurityConfig {

    @Bean
    @Order(3)
    public SecurityFilterChain adminSecurityFilterChain(HttpSecurity http,
            JwtVerifier adminJwtVerifier, SecurityZoneProperties properties,
            ProblemAuthenticationEntryPoint entryPoint,
            ProblemAccessDeniedHandler accessDeniedHandler,
            MfaEnforcementFilter mfaEnforcementFilter,
            TokenVersionValidator tokenVersionValidator) throws Exception {

        CsrfTokenRequestAttributeHandler csrfHandler = new CsrfTokenRequestAttributeHandler();
        // Tắt nạp trễ: nếu không, cookie XSRF-TOKEN chỉ xuất hiện khi có gì đó đọc tới token,
        // và SPA sẽ không bao giờ có token để gửi kèm ở request ghi đầu tiên.
        csrfHandler.setCsrfRequestAttributeName(null);

        CookieSessionAuthenticationFilter cookieFilter =
                new CookieSessionAuthenticationFilter(adminJwtVerifier, entryPoint, tokenVersionValidator);
        StoreMdcFilter storeMdcFilter = new StoreMdcFilter();

        return http.securityMatcher("/api/v1/admin/**")
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().hasAuthority("SCOPE_" + properties.admin().scope()))
                .addFilterBefore(cookieFilter, AuthorizationFilter.class)
                .addFilterAfter(storeMdcFilter, CookieSessionAuthenticationFilter.class)
                // FR-AUTH-02, cùng lý do StaffSecurityConfig. Vùng admin chưa phát token được
                // (OPEN-07) nên nhánh này chưa có test chạm tới, nhưng dây sẵn để không quên.
                .addFilterAfter(mfaEnforcementFilter, CookieSessionAuthenticationFilter.class)
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(csrfHandler))
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(cache -> cache.disable())
                .anonymous(anonymous -> anonymous.disable())
                .build();
    }
}
