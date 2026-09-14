package com.qros.shared.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.filter.OncePerRequestFilter;

import com.qros.shared.error.ErrorCode;
import com.qros.shared.error.ProblemDetailFactory;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import tools.jackson.databind.ObjectMapper;

/**
 * Chặn thao tác ghi khi token mang claim {@code mfaBlocked: true} — {@code FR-AUTH-02}:
 * {@code STORE_MANAGER}/{@code ADMIN} chưa bật MFA thì chưa có quyền ghi, kể cả khi đã đăng nhập
 * hợp lệ.
 *
 * <p>Claim {@code mfaBlocked} được tính một lần lúc đăng nhập (identity module — xem
 * {@code AuthenticationService.claimsFor}), không phải ở đây: filter này chỉ đọc claim đã có sẵn
 * trong token, không tự tra CSDL, nên vẫn ở {@code shared} được (không phụ thuộc nghiệp vụ). Hệ quả
 * là bật MFA xong vẫn phải chờ token cũ hết hạn (tối đa 15 phút) mới hết bị chặn — chấp nhận được
 * vì access token sống ngắn.
 *
 * <p>Chỉ chặn phương thức ghi; {@code GET}/{@code HEAD}/{@code OPTIONS} luôn đi qua, vì tài khoản
 * chưa bật MFA vẫn phải xem được để tự đăng ký MFA.
 */
public class MfaEnforcementFilter extends OncePerRequestFilter {

    private static final Set<String> PHUONG_THUC_DOC = Set.of(
            HttpMethod.GET.name(), HttpMethod.HEAD.name(), HttpMethod.OPTIONS.name());

    private final ProblemDetailFactory problemDetailFactory;
    private final ObjectMapper objectMapper;

    public MfaEnforcementFilter(ProblemDetailFactory problemDetailFactory, ObjectMapper objectMapper) {
        this.problemDetailFactory = problemDetailFactory;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        if (PHUONG_THUC_DOC.contains(request.getMethod()) || !bdoiTuongDangBiChan()) {
            filterChain.doFilter(request, response);
            return;
        }

        ProblemDetail problem = problemDetailFactory.create(ErrorCode.MFA_REQUIRED, request);
        response.setStatus(ErrorCode.MFA_REQUIRED.status().value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(problem));
    }

    private boolean bdoiTuongDangBiChan() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication != null && authentication.getPrincipal() instanceof Jwt jwt)) {
            // Không có token hợp lệ: không phải việc của filter này, ProblemAuthenticationEntryPoint
            // đã/sẽ xử lý ở bước phân quyền.
            return false;
        }
        Boolean mfaBlocked = jwt.getClaimAsBoolean("mfaBlocked");
        return Boolean.TRUE.equals(mfaBlocked);
    }
}
