package com.qros.shared.idempotency;

/**
 * Kết quả được lưu lại để trả nguyên vẹn cho lần gửi lặp.
 *
 * @param status   mã HTTP đã trả lần đầu — đây là mã {@link IdempotencyGuard} LƯU LẠI, không nhất
 *                 thiết là mã trả về lượt sau; xem {@link #replayed()}.
 * @param body     thân phản hồi; {@code null} cho phản hồi không có nội dung.
 * @param replayed {@code true} nếu đây là kết quả của một khoá ĐÃ TỪNG chạy (lượt này không chạy
 *                 lại tác dụng phụ) — caller cần cờ này để tự quyết mã HTTP thật sự trả cho lượt
 *                 gửi lặp khi hợp đồng đòi một mã khác lượt đầu (ví dụ {@code createOrder}: lượt
 *                 đầu {@code 201}, gửi lặp {@code 200} — quyết định đó thuộc về hợp đồng của từng
 *                 endpoint, không phải của kernel idempotency).
 */
public record IdempotentResponse<T>(int status, T body, boolean replayed) {

    /** Constructor cho phía action: luôn là lượt chạy thật, {@code replayed} luôn {@code false}. */
    public IdempotentResponse(int status, T body) {
        this(status, body, false);
    }
}
