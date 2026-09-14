package com.qros.shared.observability;

import java.util.regex.Pattern;

/**
 * Che dữ liệu nhạy cảm trong nội dung log tự do — {@code NFR-OBS-03}: "Dữ liệu cá nhân bị che ở
 * tầng appender, không phụ thuộc vào kỷ luật của lập trình viên".
 *
 * <p>Đặt ở tầng appender (xem {@code MaskedMessageJsonProvider}) có nghĩa là hàm này chạy trên
 * **mọi** dòng log, kể cả dòng do lập trình viên vô ý viết ra chuỗi có mật khẩu/token — khác với
 * dựa vào việc mọi người tự nhớ không log những thứ đó.
 *
 * <p>Đơn giản và bảo thủ có chủ ý: regex bắt theo mẫu {@code key=value}/{@code key: value} và các
 * dạng chuỗi tự thân đã nhận diện được (JWT, số thẻ) — không cố phân tích cú pháp. Che nhầm một
 * chuỗi vô hại còn chấp nhận được hơn để lọt một bí mật thật.
 */
public final class PiiMasker {

    private static final String MASK = "***";

    /**
     * {@code password=...}, {@code mat_khau: ...}, {@code pin=...}, hoặc dạng JSON
     * {@code "password":"..."} — khoá có thể có ngoặc kép bao quanh hoặc không.
     */
    private static final Pattern KEY_VALUE_SECRET = Pattern.compile(
            "(?i)\\b(password|mat_?khau|pin|token|secret|api[_-]?key|authorization)\"?"
                    + "\\s*[=:]\\s*\"?[^\\s,;\"]+\"?");

    /** JWS/JWT: ba đoạn base64url nối bằng dấu chấm — đúng hình dạng token vùng bảo mật phát ra. */
    private static final Pattern JWT_LIKE =
            Pattern.compile("\\b[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\b");

    /** Email — PII theo {@code TM-AI-02}. */
    private static final Pattern EMAIL =
            Pattern.compile("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b");

    /** Chuỗi 13–19 chữ số liền nhau hoặc cách bởi khoảng trắng/gạch ngang — hình dạng số thẻ. */
    private static final Pattern CARD_LIKE =
            Pattern.compile("\\b(?:\\d[ -]?){13,19}\\b");

    private PiiMasker() {
    }

    public static String mask(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String masked = KEY_VALUE_SECRET.matcher(text).replaceAll(match ->
                match.group(1) + "=" + MASK);
        masked = JWT_LIKE.matcher(masked).replaceAll(MASK);
        masked = EMAIL.matcher(masked).replaceAll(MASK);
        masked = CARD_LIKE.matcher(masked).replaceAll(MASK);
        return masked;
    }
}
