package com.qros.venue.api;

import java.util.Optional;
import java.util.UUID;

/**
 * Cổng duy nhất để module khác đọc thông tin chi nhánh — {@code catalog} ({@code BL-M1-02}) cần
 * {@code timezone} để tính giá theo lịch ({@code FR-MGT-04}) đúng giờ địa phương của chi nhánh,
 * không phải giờ máy chủ. Đây là lần đầu {@code venue} có {@code api}: thêm khi có nhu cầu thật
 * thay vì đoán trước, cùng nguyên tắc đã ghi ở {@code identity}/{@code audit}.
 */
public interface StoreFacade {

    Optional<StoreView> find(UUID storeId);
}
