package com.qros.shared.idempotency;

import java.time.Clock;
import java.time.Instant;

import org.springframework.transaction.support.TransactionTemplate;

/**
 * Dọn khoá đã hết hạn.
 *
 * <p>Khoá hết hạn không còn ảnh hưởng tới tính đúng đắn — {@link IdempotencyRepository#claim} đã
 * chiếm lại được chúng. Việc dọn chỉ để bảng không phình vô hạn; chỉ mục {@code ix_idempotency_expiry}
 * trong {@code V1} có sẵn cho đúng câu truy vấn này.
 */
public class IdempotencySweeper {

    private final TransactionTemplate transactionTemplate;
    private final IdempotencyRepository repository;
    private final Clock clock;

    public IdempotencySweeper(TransactionTemplate transactionTemplate,
            IdempotencyRepository repository, Clock clock) {
        this.transactionTemplate = transactionTemplate;
        this.repository = repository;
        this.clock = clock;
    }

    public int sweepOnce() {
        Integer deleted = transactionTemplate.execute(status ->
                repository.deleteExpired(Instant.now(clock)));
        return deleted != null ? deleted : 0;
    }
}
