package com.qros.shared.error;

import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

/**
 * Bảng ánh xạ duy nhất giữa mã lỗi ổn định, mã HTTP và tiêu đề hiển thị (SDD mục 11).
 *
 * <p>Mỗi mã ứng với đúng một mã HTTP. Client bắt theo {@code code} chứ không theo văn bản, nên
 * tên hằng là một phần hợp đồng: đổi tên là đổi hợp đồng, phải sửa {@code docs/api/openapi.yaml}
 * trước. Tiêu đề viết bằng tiếng Việt và chỉ nói chuyện gì đã xảy ra ở mức nghiệp vụ — không
 * tên class, không câu truy vấn, không stack trace ({@code NFR-SEC} A05, {@code TM-OPS-02}).
 */
public enum ErrorCode {

    // ── Lỗi giao vận, do tầng web sinh ra ────────────────────────────────────
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Dữ liệu gửi lên không hợp lệ"),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "Nội dung yêu cầu không đọc được"),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "Cần đăng nhập hoặc phiên đã hết hạn"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "Không có quyền thực hiện thao tác này"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy dữ liệu"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "Phương thức không được hỗ trợ"),
    NOT_ACCEPTABLE(HttpStatus.NOT_ACCEPTABLE, "Không có định dạng phản hồi phù hợp"),
    REQUEST_REJECTED(HttpStatus.BAD_REQUEST, "Yêu cầu bị từ chối"),
    PAYLOAD_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "Nội dung yêu cầu quá lớn"),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Định dạng nội dung không được hỗ trợ"),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "Thao tác quá nhanh, vui lòng thử lại sau"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Hệ thống gặp sự cố"),
    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Dịch vụ tạm thời không khả dụng"),

    // ── Phiên bàn và mã QR — openapi.yaml, nhóm guest-session ────────────────
    QR_INVALID_SIGNATURE(HttpStatus.UNAUTHORIZED, "Mã QR không hợp lệ"),
    QR_OTP_EXPIRED(HttpStatus.UNAUTHORIZED, "Mã QR đã hết hiệu lực, vui lòng quét lại"),
    TABLE_CODE_INVALID(HttpStatus.UNAUTHORIZED, "Mã bàn không đúng, vui lòng kiểm tra lại"),
    STORE_CLOSED(HttpStatus.FORBIDDEN, "Chi nhánh đang ngoài giờ phục vụ"),
    TABLE_SESSION_CONFLICT(HttpStatus.CONFLICT, "Bàn đang có phiên khác, cần nhân viên xử lý"),
    TABLE_SESSION_EXPIRED(HttpStatus.UNAUTHORIZED, "Phiên bàn đã hết hạn, vui lòng quét lại mã QR"),

    // ── Đặt món — openapi.yaml, nhóm guest-order ─────────────────────────────
    PRICE_NOT_ACCEPTED(HttpStatus.BAD_REQUEST, "Máy chủ không nhận giá từ phía khách"),
    PRICE_CHANGED(HttpStatus.CONFLICT, "Giá đã thay đổi, vui lòng xác nhận lại"),
    ITEM_SOLD_OUT(HttpStatus.CONFLICT, "Món vừa hết"),
    ORDER_RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "Đặt món quá nhanh, vui lòng thử lại sau"),
    INVALID_TRANSITION(HttpStatus.UNPROCESSABLE_CONTENT, "Trạng thái hiện tại không cho phép thao tác này"),
    VERSION_CONFLICT(HttpStatus.CONFLICT, "Người khác vừa cập nhật, vui lòng tải lại"),
    REASON_REQUIRED(HttpStatus.UNPROCESSABLE_CONTENT, "Thao tác này cần ghi lý do"),
    // V1__baseline.sql, bảng idempotency_key: cùng khoá nhưng khác nội dung phải trả 422,
    // chứ không trả nhầm kết quả của một yêu cầu khác.
    IDEMPOTENCY_KEY_REUSED(HttpStatus.UNPROCESSABLE_CONTENT, "Khoá idempotency đã dùng cho yêu cầu khác"),

    // ── Thanh toán — openapi.yaml, nhóm guest-payment và webhook ─────────────
    PAYMENT_ALREADY_SETTLED(HttpStatus.CONFLICT, "Hoá đơn đã được thanh toán"),
    WEBHOOK_SIGNATURE_INVALID(HttpStatus.UNAUTHORIZED, "Chữ ký webhook không hợp lệ"),

    // ── Xác thực nhân viên — openapi.yaml, nhóm auth ─────────────────────────
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Thông tin đăng nhập không đúng"),
    ACCOUNT_LOCKED(HttpStatus.LOCKED, "Tài khoản tạm khoá, vui lòng thử lại sau"),
    REFRESH_REUSED(HttpStatus.UNAUTHORIZED, "Phiên đăng nhập đã bị thu hồi, vui lòng đăng nhập lại"),
    // FR-AUTH-02: STORE_MANAGER/ADMIN phải bật MFA trước khi được cấp quyền ghi.
    MFA_REQUIRED(HttpStatus.FORBIDDEN, "Cần bật xác thực hai lớp trước khi thực hiện thao tác này"),

    // ── Trợ lý AI — openapi.yaml, nhóm guest-assistant ───────────────────────
    ASSISTANT_RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "Trợ lý đang bận, vui lòng thử lại sau"),
    AI_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Trợ lý tạm thời không khả dụng");

    private final HttpStatus status;
    private final String title;

    ErrorCode(HttpStatus status, String title) {
        this.status = status;
        this.title = title;
    }

    public HttpStatus status() {
        return status;
    }

    public String title() {
        return title;
    }

    /**
     * URN thay vì URL: {@code type} chỉ cần định danh ổn định, không cần trỏ tới trang tài liệu
     * có thật — dựng một URL không phân giải được chỉ tạo ra liên kết chết trong log sự cố.
     */
    public URI type() {
        return URI.create("urn:qros:error:" + name());
    }

    /** Mã dùng khi chỉ biết mã HTTP: lỗi phát sinh ở tầng giao vận, ngoài tầm nghiệp vụ. */
    public static ErrorCode forStatus(HttpStatusCode statusCode) {
        return switch (statusCode.value()) {
            case 400 -> VALIDATION_FAILED;
            case 401 -> UNAUTHENTICATED;
            case 403 -> FORBIDDEN;
            case 404 -> NOT_FOUND;
            case 405 -> METHOD_NOT_ALLOWED;
            case 406 -> NOT_ACCEPTABLE;
            case 413 -> PAYLOAD_TOO_LARGE;
            case 415 -> UNSUPPORTED_MEDIA_TYPE;
            case 429 -> RATE_LIMITED;
            case 503 -> SERVICE_UNAVAILABLE;
            default -> statusCode.is4xxClientError() ? REQUEST_REJECTED : INTERNAL_ERROR;
        };
    }
}
