package com.qros.shared.security;

import java.io.IOException;
import java.util.Collection;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * {@code NFR-OBS-03}: chỉ đưa chi nhánh đang hoạt động đã được token cho phép vào MDC.
 *
 * <p>{@code X-Store-Id} là dữ liệu do client gửi. Filter chạy sau {@link CookieSessionAuthenticationFilter},
 * nên chỉ nhận UUID chuẩn có trong claim {@code stores}; service vẫn là nơi thực thi authorization và trả
 * 404 cho request sai phạm vi.
 */
public final class StoreMdcFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Store-Id";
    public static final String MDC_KEY = "storeId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String storeId = verifiedStoreId(request.getHeader(HEADER));
        if (storeId != null) {
            MDC.put(MDC_KEY, storeId);
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            // Thread của servlet container tái sử dụng; không để store cũ dính vào log request sau.
            MDC.remove(MDC_KEY);
        }
    }

    private static String verifiedStoreId(String header) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication != null && authentication.getPrincipal() instanceof Jwt jwt) || header == null) {
            return null;
        }

        UUID parsed;
        try {
            parsed = UUID.fromString(header);
        } catch (IllegalArgumentException exception) {
            return null;
        }
        // UUID.fromString chấp nhận vài dạng rút gọn; log chỉ nhận biểu diễn canonical.
        String canonical = parsed.toString();
        if (!canonical.equals(header)) {
            return null;
        }

        Collection<String> stores = jwt.getClaimAsStringList("stores");
        if (stores == null || (!stores.contains("*") && !stores.contains(canonical))) {
            return null;
        }
        return canonical;
    }
}
