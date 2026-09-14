package com.qros.shared.web;

import java.util.regex.Pattern;

import org.slf4j.MDC;

import com.qros.shared.id.UuidV7;

/**
 * Correlation ID của request hiện tại, lưu trong MDC để mọi dòng log đi kèm mà không cần
 * lập trình viên nhớ truyền tay ({@code NFR-OBS-03}).
 *
 * <p>Giá trị đến từ client được kiểm định nghiêm ngặt trước khi dùng lại. Header do bên ngoài
 * điều khiển sẽ đi vào log và đi ngược ra response, nên nếu nhận bừa thì mở đường cho chèn
 * dòng log giả (CRLF) và cho payload dài bơm phồng log.
 */
public final class CorrelationId {

    /** Header trao đổi hai chiều: đọc từ request nếu hợp lệ, luôn ghi lại vào response. */
    public static final String HEADER = "X-Correlation-Id";

    /** Khoá MDC; pattern log JSON của {@code BL-M0-11} sẽ đọc đúng khoá này. */
    public static final String MDC_KEY = "correlationId";

    private static final Pattern SAFE_VALUE = Pattern.compile("[A-Za-z0-9_-]{8,64}");

    private CorrelationId() {
    }

    /** Correlation ID hiện tại, hoặc {@code null} khi đang ở ngoài phạm vi một request. */
    public static String current() {
        return MDC.get(MDC_KEY);
    }

    public static String generate() {
        return UuidV7.generate().toString();
    }

    /** Trả về giá trị nếu dùng lại được, ngược lại {@code null} để phía gọi tự sinh mới. */
    public static String sanitize(String candidate) {
        return candidate != null && SAFE_VALUE.matcher(candidate).matches() ? candidate : null;
    }

    static void set(String correlationId) {
        MDC.put(MDC_KEY, correlationId);
    }

    static void clear() {
        MDC.remove(MDC_KEY);
    }
}
