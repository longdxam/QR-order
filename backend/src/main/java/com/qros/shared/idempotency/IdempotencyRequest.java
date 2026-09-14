package com.qros.shared.idempotency;

import java.util.Objects;
import java.util.UUID;

/**
 * Danh tính của một yêu cầu có tác dụng phụ.
 *
 * @param key       giá trị header {@code Idempotency-Key} (UUIDv4 do client sinh).
 * @param scope     định danh thao tác, ví dụ {@code guest.createOrder}. Cùng khoá dùng cho hai
 *                  thao tác khác nhau là lỗi client, không phải một lần thử lại.
 * @param sessionId phiên bàn hoặc phiên nhân viên sở hữu khoá; {@code null} nếu không thuộc phiên.
 * @param body      thân yêu cầu ở dạng đã tuần tự hoá, dùng để lấy vân tay.
 */
public record IdempotencyRequest(UUID key, String scope, UUID sessionId, String body) {

    public IdempotencyRequest {
        Objects.requireNonNull(key, "key không được null");
        Objects.requireNonNull(scope, "scope không được null");
        Objects.requireNonNull(body, "body không được null");
    }
}
