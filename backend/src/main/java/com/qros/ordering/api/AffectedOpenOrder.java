package com.qros.ordering.api;

import java.util.List;
import java.util.UUID;

/** Đơn đang chờ có dòng bị ảnh hưởng bởi một nguyên liệu vừa báo hết. */
public record AffectedOpenOrder(UUID orderId, UUID sessionId, List<AffectedOrderLine> lines) {

    public record AffectedOrderLine(UUID lineId, String itemName, long refundAmount) {
    }
}
