package com.qros.audit.api;

/**
 * Cổng duy nhất để module khác ghi một thao tác nhạy cảm vào nhật ký bất biến
 * ({@code TM-REP-01}). Không có phương thức đọc hay sửa ở đây có chủ ý — đọc lại (cho trình xem
 * audit log, {@code FR-MGT-12}) thuộc M3, và sửa/xoá không bao giờ tồn tại.
 */
public interface AuditRecorder {

    void record(AuditEntry entry);
}
