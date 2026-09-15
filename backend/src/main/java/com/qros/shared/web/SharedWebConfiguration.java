package com.qros.shared.web;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;


import io.micrometer.tracing.Tracer;
import jakarta.servlet.DispatcherType;

/** Nạp hạ tầng web dùng chung cho cả ba vùng {@code /guest}, {@code /staff}, {@code /admin}. */
@Configuration(proxyBeanMethods = false)
public class SharedWebConfiguration {

    @Bean
    public FilterRegistrationBean<CorrelationIdFilter> correlationIdFilter() {
        FilterRegistrationBean<CorrelationIdFilter> registration =
                new FilterRegistrationBean<>(new CorrelationIdFilter());
        // Chạy trước chuỗi bảo mật: cả request bị từ chối cũng phải có correlation ID.
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.addUrlPatterns("/*");
        // Cả lượt chuyển tiếp lỗi và lượt async đều phải có MDC, không riêng lượt request gốc.
        registration.setDispatcherTypes(DispatcherType.REQUEST, DispatcherType.ERROR, DispatcherType.ASYNC);
        return registration;
    }

    /**
     * {@code BL-M0-11}: {@code Tracer} tới từ {@code spring-boot-starter-opentelemetry}, luôn có
     * mặt (không cần {@code DataSource}) nên không cần điều kiện nào khác. Điều kiện thiếu bean
     * vẫn giữ lại — test có thể tự cấp {@link TraceIdProvider} giả mà không phải sửa lớp này.
     */
    @Bean
    @ConditionalOnMissingBean(TraceIdProvider.class)
    public TraceIdProvider traceIdProvider(Tracer tracer) {
        return new OtelTraceIdProvider(tracer);
    }
}
