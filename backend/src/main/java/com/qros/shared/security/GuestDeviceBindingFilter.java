package com.qros.shared.security;

import java.io.IOException;
import java.util.UUID;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** `TM-SES-01`: token guest phát hành mới chỉ dùng được tại đúng thiết bị đã mở phiên. */
public final class GuestDeviceBindingFilter extends OncePerRequestFilter {
    public static final String HEADER = "X-Device-Id";
    private final AuthenticationEntryPoint entryPoint;

    public GuestDeviceBindingFilter(AuthenticationEntryPoint entryPoint) {
        this.entryPoint = entryPoint;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication != null && authentication.getPrincipal() instanceof Jwt jwt)) {
            chain.doFilter(request, response);
            return;
        }
        String did = jwt.getClaimAsString("did");
        // Token trước ADR-007 không có did; chỉ có thể còn sống tối đa TTL 90 phút sau deploy.
        if (did != null && !sameUuid(did, request.getHeader(HEADER))) {
            entryPoint.commence(request, response, new BadCredentialsException("guest device mismatch"));
            return;
        }
        chain.doFilter(request, response);
    }

    private static boolean sameUuid(String claim, String header) {
        try {
            return UUID.fromString(claim).equals(UUID.fromString(header));
        } catch (IllegalArgumentException | NullPointerException exception) {
            return false;
        }
    }
}
