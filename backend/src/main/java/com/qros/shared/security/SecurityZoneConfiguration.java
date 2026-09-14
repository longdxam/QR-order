package com.qros.shared.security;

import java.time.Clock;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.qros.shared.error.ProblemDetailFactory;

import tools.jackson.databind.ObjectMapper;

/**
 * Ba bộ xác minh token, một cho mỗi vùng — nền của bất biến số 3.
 *
 * <p>Không có bean {@code JwtDecoder} dùng chung nào ở đây, và đó là điểm chính: nếu ba chuỗi filter
 * cùng lấy một decoder thì việc tách matcher chỉ là trang trí, một token hợp lệ ở vùng này sẽ hợp lệ
 * ở cả ba ({@code TM-AUTH-02}).
 *
 * <p>Cũng vì thế không có bean {@code BearerTokenResolver} nào ở đây. Spring Security tự nhặt một
 * bean loại đó và áp cho **mọi** chuỗi resource server; khai báo bộ đọc cookie thành bean sẽ khiến
 * vùng khách âm thầm đi đọc cookie và bỏ qua header. Mỗi vùng tự dựng bộ đọc của mình.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SecurityZoneProperties.class)
public class SecurityZoneConfiguration {

    @Bean
    public JwtVerifier guestJwtVerifier(SecurityZoneProperties properties, Clock clock) {
        return new JwtVerifier("guest", properties.guest(), clock);
    }

    @Bean
    public JwtVerifier staffJwtVerifier(SecurityZoneProperties properties, Clock clock) {
        return new JwtVerifier("staff", properties.staff(), clock);
    }

    @Bean
    public JwtVerifier adminJwtVerifier(SecurityZoneProperties properties, Clock clock) {
        return new JwtVerifier("admin", properties.admin(), clock);
    }

    /**
     * Admin (còn mở, xem {@code AdminSecurityConfig}, {@code OPEN-07}) chưa có bean tương ứng —
     * chỉ staff ({@code BL-M0-08}) và guest ({@code BL-M1-01}) phát token tới giờ.
     */
    @Bean
    public JwtIssuer staffJwtIssuer(SecurityZoneProperties properties, Clock clock) {
        return new JwtIssuer(properties.staff(), clock);
    }

    /** Phát token phiên bàn sau khi {@code venue} xác minh QR/mã bàn thành công — {@code BL-M1-01}. */
    @Bean
    public JwtIssuer guestJwtIssuer(SecurityZoneProperties properties, Clock clock) {
        return new JwtIssuer(properties.guest(), clock);
    }

    @Bean
    public ProblemAuthenticationEntryPoint problemAuthenticationEntryPoint(
            ProblemDetailFactory problemDetailFactory, ObjectMapper objectMapper) {
        return new ProblemAuthenticationEntryPoint(problemDetailFactory, objectMapper);
    }

    @Bean
    public ProblemAccessDeniedHandler problemAccessDeniedHandler(
            ProblemDetailFactory problemDetailFactory, ObjectMapper objectMapper) {
        return new ProblemAccessDeniedHandler(problemDetailFactory, objectMapper);
    }

    /** Dùng chung cho vùng staff và admin — {@code FR-AUTH-02}, {@code BL-M0-09}. */
    @Bean
    public MfaEnforcementFilter mfaEnforcementFilter(
            ProblemDetailFactory problemDetailFactory, ObjectMapper objectMapper) {
        return new MfaEnforcementFilter(problemDetailFactory, objectMapper);
    }

    /**
     * Luôn đồng ý — chỗ giữ chỗ để mọi profile (kể cả {@code test}, không có CSDL) khởi động được.
     * Identity module đè bằng hiện thực thật khi có {@code DataSource} (đánh dấu {@code @Primary}
     * ở đó) — xem {@code identity.service.TokenVersionValidatorImpl}.
     */
    @Bean
    public TokenVersionValidator defaultTokenVersionValidator() {
        return (subject, tokenVersion) -> true;
    }
}
