package com.qros.identity.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.transaction.support.TransactionTemplate;

import com.qros.identity.repository.WorkShiftRepository;

/**
 * Đóng ca tự động sau 12 giờ — {@code FR-AUTH-04}: "Phiên tự đóng khi kết ca hoặc sau 12 giờ".
 *
 * <p>Tách logic khỏi nhịp {@code @Scheduled}, cùng khuôn với {@code OutboxPoller}/
 * {@code IdempotencySweeper}: test gọi được từng lượt mà không phải chờ lịch thật.
 */
public class WorkShiftAutoCloser {

    static final Duration MAX_SHIFT_DURATION = Duration.ofHours(12);

    private final TransactionTemplate transactionTemplate;
    private final WorkShiftRepository repository;
    private final Clock clock;

    public WorkShiftAutoCloser(TransactionTemplate transactionTemplate, WorkShiftRepository repository,
            Clock clock) {
        this.transactionTemplate = transactionTemplate;
        this.repository = repository;
        this.clock = clock;
    }

    /** @return số ca vừa bị đóng tự động. */
    public int closeExpiredOnce() {
        Instant now = Instant.now(clock);
        Instant threshold = now.minus(MAX_SHIFT_DURATION);

        Integer daDong = transactionTemplate.execute(status -> repository.closeExpired(now, threshold));
        return daDong != null ? daDong : 0;
    }
}
