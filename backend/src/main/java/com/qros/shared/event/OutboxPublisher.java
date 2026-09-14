package com.qros.shared.event;

/**
 * Cổng phát sự kiện ra ngoài tiến trình.
 *
 * <p>Tách khỏi poller để hai thứ độc lập nhau: đổi phương tiện fan-out không phải sửa logic đọc
 * outbox, và test có thể dựng trường hợp phát thất bại mà không cần làm hỏng Redis thật.
 */
public interface OutboxPublisher {

    /**
     * Phát một sự kiện. Ném ngoại lệ nếu không phát được — poller dựa vào đó để cuộn ngược giao
     * dịch và giữ dòng ở trạng thái chưa phát.
     */
    void publish(String channel, String message);
}
