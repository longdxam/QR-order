package com.qros.shared.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import com.qros.shared.error.ErrorCode;
import com.qros.shared.error.ProblemDetailFactory;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import tools.jackson.databind.ObjectMapper;

/**
 * Trả {@code 401} theo RFC 7807 thay vì thân phản hồi rỗng mặc định của Spring Security.
 *
 * <p>Chuỗi filter nằm ngoài tầm với của {@code @RestControllerAdvice}, nên nếu không có lớp này thì
 * bất biến số 4 thủng đúng ở nơi bị gọi nhiều nhất: mọi request thiếu token. Thông điệp luôn giống
 * nhau cho mọi nguyên nhân — hết hạn, sai chữ ký, sai audience — để không ai dò được token hỏng ở
 * đâu ({@code TM-AUTH-02}, {@code TM-OPS-02}).
 */
public class ProblemAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ProblemDetailFactory problemDetailFactory;
    private final ObjectMapper objectMapper;

    public ProblemAuthenticationEntryPoint(ProblemDetailFactory problemDetailFactory,
            ObjectMapper objectMapper) {
        this.problemDetailFactory = problemDetailFactory;
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException {

        ProblemDetail problem = problemDetailFactory.create(ErrorCode.UNAUTHENTICATED, request);
        response.setStatus(ErrorCode.UNAUTHENTICATED.status().value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        // Bắt buộc đặt charset: mặc định của servlet là ISO-8859-1, và mọi tiêu đề lỗi tiếng Việt
        // sẽ về tay khách dưới dạng dấu hỏi. Tầng MVC không dính lỗi này vì bộ chuyển đổi của
        // Spring tự dùng UTF-8; hai lớp viết thẳng ra response thì phải tự khai báo.
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(problem));
    }
}
