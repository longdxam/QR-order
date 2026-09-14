package com.qros.catalog.api;

import java.util.List;
import java.util.UUID;

/**
 * Cổng duy nhất để module khác định giá một dòng đơn — {@code ordering} gọi vào đây thay vì tự đọc
 * {@code menu_item}/{@code menu_variant}/{@code option_choice}, đúng bất biến số 8 của repo
 * (giá luôn tính lại phía máy chủ từ catalog, {@code ADR-06}).
 */
public interface CatalogFacade {

    /**
     * @throws com.qros.shared.error.QrosException {@code VALIDATION_FAILED} nếu
     *         {@code menuItemId}/{@code variantId} không thuộc {@code storeId}, biến thể không
     *         thuộc món, hoặc một {@code optionChoiceId} không thuộc nhóm tuỳ chọn hợp lệ của món
     *         đó (kể cả vi phạm số lượng chọn tối thiểu/tối đa của một nhóm bắt buộc) — đây luôn là
     *         lỗi client (dữ liệu không khớp thực đơn thật), không phụ thuộc ai gọi vào.
     */
    PricedLine priceLine(UUID storeId, UUID menuItemId, UUID variantId, List<UUID> optionChoiceIds);
}
