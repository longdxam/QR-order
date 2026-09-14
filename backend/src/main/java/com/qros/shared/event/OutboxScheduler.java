package com.qros.shared.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Nhịp chạy của {@link OutboxPoller}, tách khỏi phần logic để test gọi được từng lượt.
 *
 * <p>Nuốt ngoại lệ có chủ ý: nếu để ngoại lệ thoát ra thì Spring huỷ luôn lịch chạy, và một lần
 * Redis chớp tắt sẽ làm outbox đứng im vĩnh viễn. Sự kiện chưa phát vẫn nằm nguyên trong bảng nên
 * lượt sau lấy lại được; điều cần làm là hét lên trong log chứ không phải dừng nhịp.
 */
public class OutboxScheduler {

    private static final Logger log = LoggerFactory.getLogger(OutboxScheduler.class);

    private final OutboxPoller poller;

    public OutboxScheduler(OutboxPoller poller) {
        this.poller = poller;
    }

    @Scheduled(fixedDelayString = "${qros.outbox.poll-interval:200ms}")
    public void poll() {
        try {
            poller.pollOnce();
        } catch (RuntimeException exception) {
            log.error("Lượt phát outbox thất bại; sự kiện chưa phát vẫn còn trong bảng", exception);
        }
    }
}
