package com.qros.venue.api;

import java.util.Optional;
import java.util.UUID;

/**
 * Cổng duy nhất để module khác đọc/tương tác với phiên bàn — {@code ordering} cần nó để đặt món
 * vào đúng phiên, và để gọi nhân viên ({@code FR-CUS-12}) vốn gắn với chính bảng {@code staff_call}
 * thuộc {@code venue} (cùng nhóm schema "phiên bàn" ở {@code V1__baseline.sql}), không thuộc
 * {@code ordering} dù endpoint REST của nó nằm chung interface với các endpoint đặt món.
 */
public interface TableSessionFacade {

    Optional<TableSessionView> find(UUID sessionId);

    /**
     * {@code FR-CUS-12}: giới hạn 1 lần / 90 giây / bàn. Ném {@code QrosException(RATE_LIMITED)}
     * nếu gọi quá sớm.
     */
    void recordStaffCall(UUID sessionId, String reason, String note);
}
