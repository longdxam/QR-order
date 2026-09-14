package com.qros.shared.idempotency;

import java.time.Clock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import tools.jackson.databind.ObjectMapper;

/**
 * Nạp idempotency khi và chỉ khi ứng dụng có cơ sở dữ liệu — cùng lý do đã ghi ở
 * {@code OutboxConfiguration}: profile {@code test} chạy không có DataSource.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "spring.datasource.url")
@EnableConfigurationProperties(IdempotencyProperties.class)
@EnableScheduling
public class IdempotencyConfiguration {

    @Bean
    public IdempotencyGuard idempotencyGuard(IdempotencyRepository repository,
            ObjectMapper objectMapper, IdempotencyProperties properties, Clock clock) {
        return new IdempotencyGuard(repository, objectMapper, properties, clock);
    }

    @Bean
    public IdempotencySweeper idempotencySweeper(PlatformTransactionManager transactionManager,
            IdempotencyRepository repository, Clock clock) {
        return new IdempotencySweeper(new TransactionTemplate(transactionManager), repository, clock);
    }

    @Bean
    @ConditionalOnProperty(name = "qros.idempotency.sweeper-enabled", matchIfMissing = true)
    public IdempotencySweepScheduler idempotencySweepScheduler(IdempotencySweeper sweeper) {
        return new IdempotencySweepScheduler(sweeper);
    }
}
