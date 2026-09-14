package com.qros.shared.error;

import java.util.Map;
import java.util.Objects;

/**
 * Lỗi nghiệp vụ đã biết trước, mang sẵn {@link ErrorCode} nên biết tự dịch ra HTTP.
 *
 * <p>Hai loại thông điệp tách bạch có chủ ý:
 * <ul>
 *   <li>{@code detail} đi ra cho client — do người viết chọn từng chữ, không bao giờ chứa
 *       tên class, câu truy vấn hay giá trị nội bộ ({@code TM-OPS-02});</li>
 *   <li>{@link #getMessage()} chỉ nằm trong log máy chủ.</li>
 * </ul>
 *
 * <p>{@code extensions} là các trường mở rộng RFC 7807 mà hợp đồng đã khai báo, ví dụ
 * {@code currentTotal} và {@code changedLines} của {@code PRICE_CHANGED}. Chỉ đưa vào đây thứ
 * đã có trong {@code docs/api/openapi.yaml}.
 */
public class QrosException extends RuntimeException {

    private final transient ErrorCode errorCode;
    private final String detail;
    private final transient Map<String, Object> extensions;

    public QrosException(ErrorCode errorCode) {
        this(errorCode, null, Map.of(), null);
    }

    public QrosException(ErrorCode errorCode, String detail) {
        this(errorCode, detail, Map.of(), null);
    }

    public QrosException(ErrorCode errorCode, String detail, Map<String, Object> extensions) {
        this(errorCode, detail, extensions, null);
    }

    public QrosException(ErrorCode errorCode, String detail, Map<String, Object> extensions, Throwable cause) {
        super("%s: %s".formatted(Objects.requireNonNull(errorCode, "errorCode không được null"),
                detail != null ? detail : errorCode.title()), cause);
        this.errorCode = errorCode;
        this.detail = detail;
        this.extensions = Map.copyOf(Objects.requireNonNull(extensions, "extensions không được null"));
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    /** Thông điệp cho người dùng cuối; {@code null} nghĩa là dùng tiêu đề mặc định của mã lỗi. */
    public String detail() {
        return detail;
    }

    public Map<String, Object> extensions() {
        return extensions;
    }
}
