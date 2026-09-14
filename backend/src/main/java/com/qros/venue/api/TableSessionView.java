package com.qros.venue.api;

import java.util.UUID;

/**
 * DTO duy nhất module khác cần biết về một phiên bàn — {@code ordering} ({@code BL-M1-03}) cần
 * nhãn bàn cho mã đơn/sự kiện, và biết phiên còn mở hay chưa để chặn đặt món vào phiên đã đóng.
 */
public record TableSessionView(
        UUID sessionId,
        UUID storeId,
        UUID tableId,
        String tableLabel,
        boolean open,
        boolean staffOpened) {
}
