package com.qros.catalog.api;

import java.util.List;
import java.util.UUID;

/**
 * Kết quả định giá một dòng đơn từ catalog — {@code ordering} ({@code BL-M1-03}, {@code ADR-06})
 * dùng đúng các trường này để dựng {@code order_line}, không tự tính gì thêm.
 *
 * @param available    món/biến thể còn phục vụ được tại thời điểm gọi hay không — {@code false}
 *                     thì {@code ordering} phải từ chối cả dòng với {@code ITEM_SOLD_OUT}.
 * @param unitPriceEach giá một đơn vị (biến thể + tổng phụ phí tuỳ chọn), nhân {@code quantity} ở
 *                      phía {@code ordering} để ra {@code lineTotal} — catalog không biết số lượng.
 */
public record PricedLine(
        String itemName,
        String variantName,
        String station,
        boolean available,
        long unitPriceEach,
        List<PricedOption> options) {

    public record PricedOption(UUID optionChoiceId, String name, long surcharge) {
    }
}
