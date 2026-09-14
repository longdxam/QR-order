package com.qros.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** {@code NFR-OBS-01}, {@code NFR-OBS-03} · correlation ID xuyên suốt một request. */
class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @AfterEach
    void donDep() {
        CorrelationId.clear();
    }

    @Test
    void nfrObs01_sinhIdMoiKhiClientKhongGui() throws Exception {
        MockHttpServletResponse response = loc(new MockHttpServletRequest("GET", "/api/v1/guest/menu"));

        assertThat(response.getHeader(CorrelationId.HEADER)).isNotBlank();
    }

    @Test
    void nfrObs01_dungLaiIdHopLeCuaClient() throws Exception {
        String tuClient = "0198f0a1-4b2c-7def-8123-456789abcdef";
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/guest/menu");
        request.addHeader(CorrelationId.HEADER, tuClient);

        assertThat(loc(request).getHeader(CorrelationId.HEADER)).isEqualTo(tuClient);
    }

    @Test
    void nfrObs01_thayIdKhongAnToanBangIdTuSinh() throws Exception {
        // Giá trị từ ngoài đi thẳng vào log và ngược ra header; nhận bừa là mở đường
        // cho chèn dòng log giả và cho payload dài bơm phồng log.
        String[] khongHopLe = {
                "abc\r\nX-Injected: 1",
                "ngan",
                "a".repeat(65),
                "co dau cach",
                "<script>alert(1)</script>",
        };

        for (String gia : khongHopLe) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/guest/menu");
            request.addHeader(CorrelationId.HEADER, gia);

            String duocDung = loc(request).getHeader(CorrelationId.HEADER);

            assertThat(duocDung).as("giá trị %s phải bị thay", gia).isNotEqualTo(gia);
            assertThat(CorrelationId.sanitize(gia)).isNull();
        }
    }

    @Test
    void nfrObs03_datMdcTrongLucXuLyVaXoaSauKhiXong() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/guest/menu");
        MockHttpServletResponse response = new MockHttpServletResponse();
        String[] thayTrongChuoi = new String[1];

        filter.doFilter(request, response, (req, res) -> thayTrongChuoi[0] = CorrelationId.current());

        assertThat(thayTrongChuoi[0]).isEqualTo(response.getHeader(CorrelationId.HEADER));
        // Luồng được tái sử dụng; sót MDC là gán nhầm ID cho request kế tiếp.
        assertThat(CorrelationId.current()).isNull();
    }

    @Test
    void nfrObs01_luotChuyenTiepLoiGiuNguyenIdCuaLuotGoc() throws Exception {
        // Lượt chuyển tiếp sang /error dùng lại cùng đối tượng request, nên ID của lượt gốc
        // nằm ở thuộc tính request phải thắng cả header do client gửi.
        String idLuotGoc = "0198f0a1-4b2c-7def-8123-456789abcdef";
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/guest/menu");
        request.addHeader(CorrelationId.HEADER, "0198aaaa-bbbb-7ccc-8ddd-eeeeffff0000");
        request.setAttribute(CorrelationIdFilter.REQUEST_ATTRIBUTE, idLuotGoc);

        assertThat(loc(request).getHeader(CorrelationId.HEADER)).isEqualTo(idLuotGoc);
    }

    private MockHttpServletResponse loc(MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }
}
