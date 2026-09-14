package com.qros.identity.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Nhịp chạy của {@link WorkShiftAutoCloser} — cùng lối với {@code OutboxScheduler}/
 * {@code IdempotencySweepScheduler}: nuốt ngoại lệ có chủ ý, vì để ngoại lệ thoát ra là Spring
 * huỷ lịch và ca làm không bao giờ tự đóng nữa sau một lần lỗi.
 */
public class WorkShiftAutoCloseScheduler {

    private static final Logger log = LoggerFactory.getLogger(WorkShiftAutoCloseScheduler.class);

    private final WorkShiftAutoCloser autoCloser;

    public WorkShiftAutoCloseScheduler(WorkShiftAutoCloser autoCloser) {
        this.autoCloser = autoCloser;
    }

    @Scheduled(fixedDelayString = "${qros.identity.shift-auto-close-interval:5m}")
    public void closeExpiredShifts() {
        try {
            int daDong = autoCloser.closeExpiredOnce();
            if (daDong > 0) {
                log.info("Đã tự đóng {} ca quá 12 giờ", daDong);
            }
        } catch (RuntimeException exception) {
            log.error("Lượt tự đóng ca thất bại", exception);
        }
    }
}
