package com.qros.shared.idempotency;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Cấu hình khoá idempotency.
 *
 * @param retention      thời gian giữ kết quả. {@code FR-CUS-09} chốt 24 giờ; đổi giá trị này là
 *                       đổi yêu cầu, không phải đổi cấu hình vận hành.
 * @param sweepInterval  nhịp dọn khoá đã hết hạn.
 * @param sweeperEnabled tắt để test tự gọi từng lượt dọn.
 */
@ConfigurationProperties("qros.idempotency")
public record IdempotencyProperties(
        @DefaultValue("24h") Duration retention,
        @DefaultValue("15m") Duration sweepInterval,
        @DefaultValue("true") boolean sweeperEnabled) {
}
