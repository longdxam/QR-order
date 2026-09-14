package com.qros.shared.web;

/**
 * Nguồn của {@code traceId} đi kèm mọi phản hồi lỗi RFC 7807.
 *
 * <p>Tách thành cổng riêng vì {@code NFR-OBS-01} yêu cầu ID này tương quan với truy vết
 * OpenTelemetry xuyên trình duyệt → backend → cơ sở dữ liệu. Khi {@code BL-M0-11} bật
 * OpenTelemetry, chỉ hiện thực khác được nạp; {@code shared/error} không phải sửa.
 */
public interface TraceIdProvider {

    /**
     * Trace ID của luồng hiện tại, hoặc {@code null} khi đang chạy ngoài phạm vi một request —
     * poller outbox và job theo lịch nằm ở nhánh này.
     */
    String currentTraceIdOrNull();

    /**
     * Trace ID để gắn vào phản hồi lỗi. Luôn khác {@code null}: một phản hồi RFC 7807 thiếu
     * {@code traceId} là phản hồi sai hợp đồng, kể cả khi lỗi phát sinh ngoài phạm vi request.
     */
    String currentTraceId();
}
