package com.qros.audit.repository;

import com.qros.audit.domain.AuditEvent;

/**
 * Mảnh ghi duy nhất của {@link AuditEventRepository} — tách riêng khỏi các phương thức đọc để
 * {@link AuditEventWriterImpl} là nơi duy nhất chạm {@code EntityManager.persist}, và
 * {@code persist} không bao giờ ghi đè một hàng đã có (khác {@code merge}/{@code save}).
 */
interface AuditEventWriter {

    AuditEvent append(AuditEvent event);
}
