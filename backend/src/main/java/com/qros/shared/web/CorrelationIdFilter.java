package com.qros.shared.web;

import java.io.IOException;

import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Gắn correlation ID cho mọi request trước khi bất kỳ filter nghiệp vụ nào chạy.
 *
 * <p>Đặt header vào response ngay từ đầu chuỗi, không phải lúc trả về: request bị chặn ở tầng
 * bảo mật hay ném lỗi giữa chừng vẫn phải mang được ID để nhân viên tra cứu ({@code NFR-OBS-01}).
 *
 * <p>Filter chạy lại một lần nữa ở lượt chuyển tiếp lỗi. Lượt đó phải giữ nguyên ID của lượt gốc,
 * nếu không thì dòng log ghi lúc lỗi và phản hồi khách cầm trên tay mang hai ID khác nhau — đúng
 * lúc cần tra cứu nhất. Vì vậy ID được cất trong thuộc tính request, thứ sống sót qua chuyển tiếp.
 */
public final class CorrelationIdFilter extends OncePerRequestFilter {

    static final String REQUEST_ATTRIBUTE = CorrelationIdFilter.class.getName() + ".correlationId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String correlationId = resolve(request);

        request.setAttribute(REQUEST_ATTRIBUTE, correlationId);
        CorrelationId.set(correlationId);
        response.setHeader(CorrelationId.HEADER, correlationId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            // Thread pool dùng lại luồng; sót MDC là gán nhầm ID cho request kế tiếp.
            CorrelationId.clear();
        }
    }

    private static String resolve(HttpServletRequest request) {
        if (request.getAttribute(REQUEST_ATTRIBUTE) instanceof String carried) {
            return carried;
        }
        String inbound = CorrelationId.sanitize(request.getHeader(CorrelationId.HEADER));
        return inbound != null ? inbound : CorrelationId.generate();
    }

    /** Lỗi trong request được chuyển tiếp sang {@code /error}; lượt đó cũng cần MDC. */
    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }
}
