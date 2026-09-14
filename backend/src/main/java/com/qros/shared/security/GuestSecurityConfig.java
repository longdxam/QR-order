package com.qros.shared.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Vùng khách: {@code /api/v1/guest/**} ({@code FR-AUTH-01}, {@code NFR-SEC-02}…{@code 08}).
 *
 * <p>Bất biến số 3 của repo: ba vùng, ba chuỗi filter, **không dùng chung bean cấu hình**. Vùng này
 * mang giả định nền của dự án — kẻ tấn công đã có mã QR trong tay — nên nó nhận token từ header
 * (không cookie, do đó không cần CSRF) và không cấp quyền gì ngoài phạm vi một phiên bàn.
 *
 * <p>Chỉ hai endpoint mở: đổi mã QR lấy phiên, và nhập mã bàn. Đó là hai chỗ khách chưa thể có
 * token. Mọi thứ còn lại đòi {@code SCOPE_table_session}.
 */
// Chuỗi filter chỉ có nghĩa trong ứng dụng web; job theo lịch và test không có web context
// vẫn phải khởi động được.
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Configuration(proxyBeanMethods = false)
public class GuestSecurityConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain guestSecurityFilterChain(HttpSecurity http,
            JwtVerifier guestJwtVerifier, SecurityZoneProperties properties,
            ProblemAuthenticationEntryPoint entryPoint, ProblemAccessDeniedHandler accessDeniedHandler)
            throws Exception {

        return http.securityMatcher("/api/v1/guest/**")
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/guest/sessions", "/api/v1/guest/sessions/by-code")
                        .permitAll()
                        .anyRequest().hasAuthority("SCOPE_" + properties.guest().scope()))
                .oauth2ResourceServer(oauth2 -> oauth2
                        // Khai báo tường minh: nếu để trống, Spring Security lấy bất kỳ bean
                        // BearerTokenResolver nào có trong context, kể cả bộ đọc cookie của vùng khác.
                        .bearerTokenResolver(new DefaultBearerTokenResolver())
                        .jwt(jwt -> jwt.decoder(guestJwtVerifier))
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                // Token đi qua header chứ không qua cookie, nên trình duyệt không tự đính kèm nó
                // vào request từ trang khác — điều kiện duy nhất khiến tắt CSRF là an toàn.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(cache -> cache.disable())
                .anonymous(anonymous -> anonymous.disable())
                .build();
    }
}
