package com.qros.shared.web;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;

/**
 * Hiện thực thật của {@link TraceIdProvider} bằng OpenTelemetry ({@code BL-M0-11},
 * {@code NFR-OBS-01}) — thay {@code CorrelationIdTraceIdProvider} tạm thời của {@code BL-M0-04}.
 *
 * <p>{@code Tracer} tới từ {@code spring-boot-starter-opentelemetry}: Spring MVC tự mở một span
 * cho mỗi request HTTP, nên {@link Tracer#currentSpan()} khác {@code null} trong toàn bộ vòng đời
 * request/response, kể cả khi lỗi. Ngoài phạm vi request (job theo lịch, poller outbox) không có
 * span đang mở — rơi về {@link CorrelationId#generate()} như bản tạm thời vẫn làm, để hợp đồng
 * "không bao giờ null" của {@link TraceIdProvider} luôn đúng.
 */
public final class OtelTraceIdProvider implements TraceIdProvider {

    private final Tracer tracer;

    public OtelTraceIdProvider(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public String currentTraceIdOrNull() {
        Span span = tracer.currentSpan();
        return span != null ? span.context().traceId() : null;
    }

    @Override
    public String currentTraceId() {
        String current = currentTraceIdOrNull();
        return current != null ? current : CorrelationId.generate();
    }
}
