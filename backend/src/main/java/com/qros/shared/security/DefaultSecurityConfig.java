package com.qros.shared.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Chuỗi bắt phần còn lại và từ chối mặc định.
 *
 * <p>Không có chuỗi này thì mọi đường dẫn ngoài ba vùng đi qua Spring Security mà không gặp luật
 * nào — tức là mở. Từ chối mặc định phải là hành vi của phần không khớp, không phải phần khớp.
 *
 * <p>Ba ngoại lệ được mở tường minh:
 * <ul>
 *   <li>{@code /api/v1/auth/**} — đăng nhập và xoay token tự chứng minh danh tính, chưa thể có
 *       token trước đó ({@code BL-M0-08}, {@code BL-M0-09});</li>
 *   <li>{@code /api/v1/webhooks/**} — cổng thanh toán không có phiên; chữ ký HMAC được kiểm trên
 *       raw body trước khi parse, ngay trong handler ({@code TM-PAY-01}, milestone {@code M2});</li>
 *   <li>{@code /error} — lượt chuyển tiếp lỗi của servlet container. Chặn nó thì mọi lỗi biến
 *       thành {@code 403} và {@code ProblemErrorController} không bao giờ chạy;</li>
 *   <li>{@code /actuator/health/**} và {@code /actuator/info} — probe của trình điều phối, chỉ
 *       lắng nghe ở cổng quản trị nội bộ ({@code BL-M0-02}).</li>
 * </ul>
 */
// Chuỗi filter chỉ có nghĩa trong ứng dụng web; job theo lịch và test không có web context
// vẫn phải khởi động được.
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Configuration(proxyBeanMethods = false)
public class DefaultSecurityConfig {

    @Bean
    @Order(100)
    public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http,
            ProblemAuthenticationEntryPoint entryPoint,
            ProblemAccessDeniedHandler accessDeniedHandler) throws Exception {

        return http.securityMatcher("/**")
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/error").permitAll()
                        // Probe sức khoẻ nằm ở cổng quản trị nội bộ và phải trả lời không cần
                        // xác thực, nếu không trình điều phối sẽ coi tiến trình là chết. Chỉ mở
                        // đúng health/info; endpoint nào lộ ra về sau vẫn rơi vào denyAll.
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/**").permitAll()
                        .requestMatchers("/api/v1/webhooks/**").permitAll()
                        .anyRequest().denyAll())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                // Không phiên, không cookie: các đường dẫn ở đây tự mang bằng chứng của mình.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(cache -> cache.disable())
                .anonymous(anonymous -> anonymous.disable())
                .build();
    }
}
