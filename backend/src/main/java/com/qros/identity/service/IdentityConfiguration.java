package com.qros.identity.service;

import java.time.Clock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.qros.identity.repository.WorkShiftRepository;

/**
 * Nhịp đóng ca tự động ({@code FR-AUTH-04}) — cùng khuôn {@code OutboxConfiguration}/
 * {@code IdempotencyConfiguration}: chỉ nạp khi có {@code DataSource}.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "spring.datasource.url")
@EnableScheduling
public class IdentityConfiguration {

    @Bean
    public WorkShiftAutoCloser workShiftAutoCloser(PlatformTransactionManager transactionManager,
            WorkShiftRepository repository, Clock clock) {
        return new WorkShiftAutoCloser(new TransactionTemplate(transactionManager), repository, clock);
    }

    @Bean
    public WorkShiftAutoCloseScheduler workShiftAutoCloseScheduler(WorkShiftAutoCloser autoCloser) {
        return new WorkShiftAutoCloseScheduler(autoCloser);
    }
}
