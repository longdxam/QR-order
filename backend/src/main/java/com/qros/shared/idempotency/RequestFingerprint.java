package com.qros.shared.idempotency;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Vân tay của một yêu cầu: SHA-256 trên thao tác, phiên và thân yêu cầu.
 *
 * <p>Gộp cả {@code scope} và {@code sessionId} vào vân tay có chủ ý. Nhờ vậy "khoá của phiên khác"
 * và "thân yêu cầu khác" cho ra cùng một phản hồi lỗi, không có cách nào phân biệt — client không
 * dò được rằng một khoá nào đó đang tồn tại ở phiên bên cạnh ({@code TM-ORD-02}, bất biến số 7).
 */
final class RequestFingerprint {

    private static final char SEPARATOR = '\n';

    private RequestFingerprint() {
    }

    static String of(IdempotencyRequest request) {
        String material = request.scope() + SEPARATOR + request.sessionId() + SEPARATOR + request.body();
        return HexFormat.of().formatHex(sha256(material));
    }

    private static byte[] sha256(String material) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(material.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM không có SHA-256", exception);
        }
    }
}
