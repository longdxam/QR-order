package com.qros.shared.error;

import java.net.URI;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

import com.qros.shared.web.TraceIdProvider;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Chỗ duy nhất dựng {@link ProblemDetail} trong hệ thống.
 *
 * <p>Gom về một nơi để bất biến số 4 kiểm chứng được: mọi lỗi đều là
 * {@code application/problem+json} và luôn có {@code code} lẫn {@code traceId}. Filter bảo mật
 * của {@code BL-M0-07} cũng dùng lại lớp này thay vì tự ghép JSON — hai nơi ghép tay là hai nơi
 * quên trường.
 */
@Component
public class ProblemDetailFactory {

    private final TraceIdProvider traceIdProvider;

    public ProblemDetailFactory(TraceIdProvider traceIdProvider) {
        this.traceIdProvider = traceIdProvider;
    }

    public ProblemDetail create(ErrorCode errorCode, String detail, Map<String, Object> extensions,
            HttpServletRequest request) {

        return create(errorCode, detail, extensions, requestUriOf(request));
    }

    public ProblemDetail create(ErrorCode errorCode, String detail, Map<String, Object> extensions,
            String requestUri) {

        ProblemDetail problem = ProblemDetail.forStatus(errorCode.status());
        problem.setType(errorCode.type());
        problem.setTitle(errorCode.title());
        problem.setDetail(detail != null ? detail : errorCode.title());
        problem.setProperty("code", errorCode.name());
        problem.setProperty("traceId", traceIdProvider.currentTraceId());
        // Chỉ đường dẫn, bỏ query string: tham số truy vấn có thể chứa dữ liệu khách nhập.
        instanceOf(requestUri).ifPresent(problem::setInstance);
        extensions.forEach(problem::setProperty);
        return problem;
    }

    public ProblemDetail create(ErrorCode errorCode, HttpServletRequest request) {
        return create(errorCode, null, Map.of(), request);
    }

    /**
     * Trong một lượt chuyển tiếp lỗi, {@code getRequestURI()} đã thành {@code /error};
     * đường dẫn khách thực sự gọi nằm ở thuộc tính do servlet container đặt lại.
     */
    private static String requestUriOf(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        Object forwarded = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        return forwarded instanceof String uri ? uri : request.getRequestURI();
    }

    private static Optional<URI> instanceOf(String requestUri) {
        try {
            return Optional.ofNullable(requestUri).map(URI::create);
        } catch (IllegalArgumentException ex) {
            // Đường dẫn dị dạng không đáng làm hỏng phản hồi lỗi; bỏ trường instance là đủ.
            return Optional.empty();
        }
    }
}
