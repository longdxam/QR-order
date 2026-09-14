package com.qros.shared.idempotency;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Nhịp chạy của {@link IdempotencySweeper}, tách khỏi phần logic để test gọi được từng lượt —
 * cùng lối với {@code OutboxScheduler}.
 */
public class IdempotencySweepScheduler {

    private static final Logger log = LoggerFactory.getLogger(IdempotencySweepScheduler.class);

    private final IdempotencySweeper sweeper;

    public IdempotencySweepScheduler(IdempotencySweeper sweeper) {
        this.sweeper = sweeper;
    }

    @Scheduled(fixedDelayString = "${qros.idempotency.sweep-interval:15m}")
    public void sweep() {
        try {
            int deleted = sweeper.sweepOnce();
            if (deleted > 0) {
                log.debug("Đã dọn {} khoá idempotency hết hạn", deleted);
            }
        } catch (RuntimeException exception) {
            // Như poller outbox: để ngoại lệ thoát ra là Spring huỷ lịch và dọn dẹp dừng vĩnh viễn.
            log.error("Lượt dọn khoá idempotency thất bại", exception);
        }
    }
}
