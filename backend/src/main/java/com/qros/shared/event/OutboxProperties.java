package com.qros.shared.event;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Cấu hình poller outbox.
 *
 * @param batchSize       số dòng nhận mỗi lượt; giới hạn cả thời gian giữ khoá lẫn kích thước
 *                        giao dịch. Ở tải đỉnh 120 đơn/phút/chi nhánh ({@code NFR-PERF-08}) thì
 *                        100 là dư, nhưng vẫn cần trần để một lần dồn ứ không khoá cả bảng.
 * @param pollInterval    khoảng nghỉ giữa hai lượt. Ngân sách của {@code FR-BAR-02} là 1 giây từ
 *                        lúc đặt đơn tới lúc KDS thấy, nên chu kỳ phải nhỏ hơn nhiều lần.
 * @param schedulerEnabled tắt để test tự gọi từng lượt và quan sát kết quả xác định.
 */
@ConfigurationProperties("qros.outbox")
public record OutboxProperties(
        @DefaultValue("100") int batchSize,
        @DefaultValue("200ms") Duration pollInterval,
        @DefaultValue("true") boolean schedulerEnabled) {
}
