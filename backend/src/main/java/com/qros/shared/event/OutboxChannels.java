package com.qros.shared.event;

import java.util.UUID;

/**
 * Tên kênh Redis pub/sub mà poller phát sự kiện ra ({@code ADR-05}, SDD mục 7).
 *
 * <p>Redis chỉ làm nhiệm vụ fan-out giữa các bản sao Spring Boot; bản đồ từ kênh Redis sang đích
 * STOMP ({@code /topic/kds/{storeId}}, {@code /user/queue/session/{sessionId}}) thuộc về
 * {@code BL-M1-04}. Vì vậy đặt tên theo **phạm vi người nghe** chứ không theo màn hình: một kênh
 * cho mỗi chi nhánh phục vụ cả KDS lẫn sơ đồ bàn, tầng WebSocket lọc tiếp theo loại sự kiện.
 *
 * <p>Phiên bàn được ưu tiên hơn chi nhánh: sự kiện của một phiên không bao giờ được rơi vào kênh
 * chi nhánh, nơi mọi nhân viên đều nghe được ({@code TM-ACC-01}).
 */
public final class OutboxChannels {

    static final String SESSION_PREFIX = "qros:session:";
    static final String STORE_PREFIX = "qros:store:";
    static final String GLOBAL = "qros:global";

    private OutboxChannels() {
    }

    public static String of(UUID storeId, UUID sessionId) {
        if (sessionId != null) {
            return SESSION_PREFIX + sessionId;
        }
        if (storeId != null) {
            return STORE_PREFIX + storeId;
        }
        return GLOBAL;
    }

    static String of(OutboxEntity event) {
        return of(event.getStoreId(), event.getSessionId());
    }
}
