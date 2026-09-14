package com.qros.venue.service;

import java.util.UUID;

/**
 * Bước 6 mục 5.3.2: giới hạn tần suất theo bàn trước khi tạo phiên mới, chặn quét mã QR dồn dập.
 *
 * <p>Cổng trừu tượng — cùng lối {@code shared.security.TokenVersionValidator}: bean mặc định
 * ({@code VenueConfiguration.permissiveTableScanRateLimiter}) luôn cho qua vì ngưỡng cụ thể (bao
 * nhiêu lần/bao lâu, fail-open hay fail-closed khi Redis lỗi) là quyết định chưa chốt ở
 * {@code OPEN-02}. {@link TableSessionService} vẫn gọi bước này đúng thứ tự (sau bước 5, trước khi
 * mở/tham gia phiên) để hành vi khớp sáu bước của PRD ngay khi ngưỡng thật được chốt — chỉ cần
 * thay hiện thực của bean này, không sửa luồng gọi.
 */
public interface TableScanRateLimiter {

    void checkTableScan(UUID tableId, String clientIp);
}
