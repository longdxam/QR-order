package com.qros.shared.time;

import java.time.Clock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Nguồn thời gian dùng chung của kernel.
 *
 * <p>Tách thành bean để test tua được thời gian — hạn token và cửa sổ 24 giờ của khoá idempotency
 * đều là hành vi phụ thuộc đồng hồ, và chờ thật thì không kiểm được. Múi giờ hạ tầng luôn là UTC;
 * múi giờ nghiệp vụ lấy từ từng chi nhánh.
 */
@Configuration(proxyBeanMethods = false)
public class ClockConfiguration {

    @Bean
    @ConditionalOnMissingBean(Clock.class)
    public Clock clock() {
        return Clock.systemUTC();
    }
}
