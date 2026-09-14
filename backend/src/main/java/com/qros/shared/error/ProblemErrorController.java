package com.qros.shared.error;

import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Lưới cuối cùng cho lỗi không đi qua controller: ngoại lệ ném từ filter, request bị servlet
 * container từ chối, hay lỗi phát sinh sau khi {@link GlobalExceptionHandler} đã hết phần.
 *
 * <p>Thay thế {@code BasicErrorController} của Spring Boot, vốn trả JSON kiểu khác và có thể kèm
 * đường dẫn cùng thông điệp nội bộ. Không có lớp này thì bất biến "mọi lỗi là RFC 7807 kèm
 * {@code code} và {@code traceId}" chỉ đúng ở nhánh may mắn — và chuỗi filter bảo mật của
 * {@code BL-M0-07} sẽ rơi đúng vào nhánh còn lại.
 */
@RestController
public class ProblemErrorController implements ErrorController {

    private final ProblemDetailFactory problemDetailFactory;

    public ProblemErrorController(ProblemDetailFactory problemDetailFactory) {
        this.problemDetailFactory = problemDetailFactory;
    }

    // Không khai báo produces: trình duyệt gửi Accept: text/html vẫn phải nhận được lỗi,
    // chứ không phải một 406 chồng lên lỗi gốc.
    @RequestMapping("${server.error.path:${error.path:/error}}")
    public ResponseEntity<ProblemDetail> handleError(HttpServletRequest request) {
        ErrorCode errorCode = ErrorCode.forStatus(statusOf(request));
        ProblemDetail problem = problemDetailFactory.create(errorCode, request);
        return ResponseEntity.status(errorCode.status())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    private static HttpStatus statusOf(HttpServletRequest request) {
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        if (status instanceof Integer code) {
            HttpStatus resolved = HttpStatus.resolve(code);
            if (resolved != null) {
                return resolved;
            }
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }
}
