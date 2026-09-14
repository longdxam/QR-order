package com.qros.shared.event;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.qros.shared.web.TraceIdProvider;

import tools.jackson.databind.ObjectMapper;

/**
 * Nạp outbox khi và chỉ khi ứng dụng có cơ sở dữ liệu.
 *
 * <p>Profile {@code test} cố tình chạy không có DataSource để context khởi động độc lập
 * ({@code BL-M0-02}); outbox thì vô nghĩa nếu không có bảng để ghi. Điều kiện đặt trên
 * {@code spring.datasource.url} chứ không trên bean {@code DataSource} vì điều kiện kiểu bean
 * trong cấu hình của ứng dụng được đánh giá **trước** khi auto-configuration đăng ký DataSource,
 * nên nó sẽ luôn sai — một cái bẫy im lặng đúng nghĩa.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "spring.datasource.url")
@EnableConfigurationProperties(OutboxProperties.class)
@EnableScheduling
public class OutboxConfiguration {

    @Bean
    public OutboxWriter outboxWriter(OutboxRepository repository, ObjectMapper objectMapper,
            TraceIdProvider traceIdProvider) {
        return new OutboxWriter(repository, objectMapper, traceIdProvider);
    }

    @Bean
    public OutboxPublisher outboxPublisher(StringRedisTemplate redisTemplate) {
        return new RedisOutboxPublisher(redisTemplate);
    }

    @Bean
    public OutboxPoller outboxPoller(PlatformTransactionManager transactionManager,
            OutboxRepository repository, OutboxPublisher publisher, ObjectMapper objectMapper,
            OutboxProperties properties) {
        return new OutboxPoller(new TransactionTemplate(transactionManager), repository, publisher,
                objectMapper, properties);
    }

    @Bean
    @ConditionalOnProperty(name = "qros.outbox.scheduler-enabled", matchIfMissing = true)
    public OutboxScheduler outboxScheduler(OutboxPoller poller) {
        return new OutboxScheduler(poller);
    }
}
