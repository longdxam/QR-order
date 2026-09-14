package com.qros.shared.error;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.servlet.http.HttpServletRequest;

import tools.jackson.databind.exc.UnrecognizedPropertyException;

/**
 * Biến mọi ngoại lệ thoát khỏi controller thành {@code application/problem+json}.
 *
 * <p>Bất biến số 4 của repo: không có đường nào khác để lỗi rời khỏi máy chủ. Kể cả ngoại lệ
 * do Spring MVC tự ném (payload sai lược đồ, sai phương thức, sai kiểu nội dung) cũng bị dựng
 * lại thân phản hồi ở đây thay vì dùng nguyên bản của framework — thông điệp mặc định của
 * framework nhắc tới tên kiểu tham số và định dạng nội bộ, tức là rò rỉ nhỏ theo
 * {@code TM-OPS-02}.
 *
 * <p>Ngoại lệ không lường trước chỉ được ghi log kèm correlation ID; client nhận
 * {@code INTERNAL_ERROR} trống rỗng và {@code traceId} để nhân viên tra ngược.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * {@code TM-AUTH-01}, runbook {@code dang-nhap-that-bai-hang-loat.md} bước 1: hai mã này phải
     * lên log ở mức {@code INFO} kèm IP nguồn, không chỉ {@code DEBUG} như lỗi nghiệp vụ khác —
     * diễn tập runbook ({@code BL-M0-14}) phát hiện ở mức log mặc định (root {@code INFO}) không có
     * dòng nào để tra traceId ngược ra IP, vì nhánh {@code else} vốn dùng {@code log.debug}.
     */
    private static final Set<ErrorCode> DANG_NHAP_THAT_BAI = Set.of(
            ErrorCode.INVALID_CREDENTIALS, ErrorCode.ACCOUNT_LOCKED);

    private final ProblemDetailFactory problemDetailFactory;

    public GlobalExceptionHandler(ProblemDetailFactory problemDetailFactory) {
        this.problemDetailFactory = problemDetailFactory;
    }

    @ExceptionHandler(QrosException.class)
    public ResponseEntity<ProblemDetail> handleQrosException(QrosException exception,
            HttpServletRequest request) {

        ProblemDetail problem = problemDetailFactory.create(
                exception.errorCode(), exception.detail(), exception.extensions(), request);

        if (exception.errorCode().status().is5xxServerError()) {
            log.error("Lỗi nghiệp vụ phía máy chủ: {}", exception.errorCode(), exception);
        } else if (DANG_NHAP_THAT_BAI.contains(exception.errorCode())) {
            log.info("Đăng nhập thất bại: {} remoteAddr={}", exception.errorCode(), request.getRemoteAddr());
        } else if (log.isDebugEnabled()) {
            log.debug("Từ chối yêu cầu: {}", exception.errorCode(), exception);
        }
        return problemResponse(problem);
    }

    @ExceptionHandler(Throwable.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Throwable throwable, HttpServletRequest request) {
        log.error("Ngoại lệ không lường trước", throwable);
        return problemResponse(problemDetailFactory.create(ErrorCode.INTERNAL_ERROR, request));
    }

    /**
     * Điểm hội tụ của mọi ngoại lệ mà {@link ResponseEntityExceptionHandler} tự xử lý.
     * Ghi đè ở đây thay vì ghi đè từng phương thức con để không thể bỏ sót nhánh nào.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception, Object body,
            HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {

        ErrorCode errorCode = errorCodeFor(exception, statusCode);
        ProblemDetail problem = problemDetailFactory.create(
                errorCode, null, Map.of(), servletRequestOf(request));

        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.addAll(headers);
        responseHeaders.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        return new ResponseEntity<>(problem, responseHeaders, errorCode.status());
    }

    /**
     * Tên trường "trông như giá" — chặn đúng bất biến số 1: client không bao giờ được gửi giá.
     * Khớp theo chuỗi con, không phân biệt hoa/thường, để bắt được mọi biến thể hợp lý
     * ({@code unitPrice}, {@code totalAmount}, {@code lineTotal}, {@code surcharge}...).
     */
    private static final String[] TU_KHOA_TRUONG_GIA = {"price", "amount", "total", "surcharge", "discount"};

    private static ErrorCode errorCodeFor(Exception exception, HttpStatusCode statusCode) {
        return switch (exception) {
            case HttpMessageNotReadableException notReadable -> tuChoiPhanTichDuoc(notReadable);
            case HttpRequestMethodNotSupportedException ignored -> ErrorCode.METHOD_NOT_ALLOWED;
            case HttpMediaTypeNotSupportedException ignored -> ErrorCode.UNSUPPORTED_MEDIA_TYPE;
            case HttpMediaTypeNotAcceptableException ignored -> ErrorCode.NOT_ACCEPTABLE;
            case NoResourceFoundException ignored -> ErrorCode.NOT_FOUND;
            case NoHandlerFoundException ignored -> ErrorCode.NOT_FOUND;
            default -> ErrorCode.forStatus(statusCode);
        };
    }

    /**
     * {@code ADR-06}, {@code FR-CUS-08}: {@code additionalProperties: false} trong
     * {@code openapi.yaml} chỉ là mô tả hợp đồng — Jackson mặc định ÂM THẦM BỎ QUA trường lạ trừ
     * khi bật {@code spring.jackson.deserialization.fail-on-unknown-properties} (đã bật ở
     * {@code application.yml}). Khi trường bị từ chối đó trông như một trường giá, phải trả đúng
     * {@code PRICE_NOT_ACCEPTED} theo hợp đồng — không phải {@code MALFORMED_REQUEST} chung chung —
     * để client (và test tampering) phân biệt được "bạn cố gửi giá" với "payload sai định dạng".
     */
    private static ErrorCode tuChoiPhanTichDuoc(HttpMessageNotReadableException exception) {
        Throwable canNguyen = exception.getCause();
        if (canNguyen instanceof UnrecognizedPropertyException unrecognized) {
            String tenTruong = unrecognized.getPropertyName();
            boolean giongTruongGia = tenTruong != null && Arrays.stream(TU_KHOA_TRUONG_GIA)
                    .anyMatch(tuKhoa -> tenTruong.toLowerCase(Locale.ROOT).contains(tuKhoa));
            return giongTruongGia ? ErrorCode.PRICE_NOT_ACCEPTED : ErrorCode.VALIDATION_FAILED;
        }
        return ErrorCode.MALFORMED_REQUEST;
    }

    private static HttpServletRequest servletRequestOf(WebRequest request) {
        return request instanceof org.springframework.web.context.request.ServletWebRequest servletWebRequest
                ? servletWebRequest.getRequest()
                : null;
    }

    private static ResponseEntity<ProblemDetail> problemResponse(ProblemDetail problem) {
        return ResponseEntity.status(problem.getStatus())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }
}
