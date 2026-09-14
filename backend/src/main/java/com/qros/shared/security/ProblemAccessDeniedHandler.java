package com.qros.shared.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

import com.qros.shared.error.ErrorCode;
import com.qros.shared.error.ProblemDetailFactory;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import tools.jackson.databind.ObjectMapper;

/** Trả {@code 403} theo RFC 7807 — cùng lý do với {@link ProblemAuthenticationEntryPoint}. */
public class ProblemAccessDeniedHandler implements AccessDeniedHandler {

    private final ProblemDetailFactory problemDetailFactory;
    private final ObjectMapper objectMapper;

    public ProblemAccessDeniedHandler(ProblemDetailFactory problemDetailFactory,
            ObjectMapper objectMapper) {
        this.problemDetailFactory = problemDetailFactory;
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {

        ProblemDetail problem = problemDetailFactory.create(ErrorCode.FORBIDDEN, request);
        response.setStatus(ErrorCode.FORBIDDEN.status().value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        // Bắt buộc đặt charset: mặc định của servlet là ISO-8859-1, và mọi tiêu đề lỗi tiếng Việt
        // sẽ về tay khách dưới dạng dấu hỏi. Tầng MVC không dính lỗi này vì bộ chuyển đổi của
        // Spring tự dùng UTF-8; hai lớp viết thẳng ra response thì phải tự khai báo.
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(problem));
    }
}
