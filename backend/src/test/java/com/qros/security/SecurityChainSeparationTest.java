package com.qros.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Bất biến số 3: ba vùng, ba chuỗi filter, và phần không thuộc vùng nào thì đóng
 * ({@code FR-AUTH-01}, {@code NFR-SEC-05}).
 *
 * <p>Trọng tâm ở đây là ranh giới chứ không phải token: vùng nào bật CSRF, vùng nào không, và điều
 * gì xảy ra với đường dẫn không khớp vùng nào.
 */
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SecurityChainSeparationTest extends SecurityZoneTestSupport {

    @Test
    void nfrSec05_vungNhanVienChanGhiKhiThieuTokenCsrf() throws Exception {
        // Token nằm trong cookie nên trình duyệt tự gửi kèm; thiếu lớp CSRF thì một trang bất kỳ
        // cũng kích hoạt được thao tác ghi của nhân viên đang đăng nhập.
        HttpResponse<String> phanHoi = post("/api/v1/staff/ping", cookie(staffToken()));

        assertThat(phanHoi.statusCode()).isEqualTo(403);
        assertThat(phanHoi.body()).contains("\"code\":\"FORBIDDEN\"").contains("traceId");
    }

    @Test
    void nfrSec05_vungNhanVienChoGhiKhiCoTokenCsrfHopLe() throws Exception {
        HttpResponse<String> doTruoc = get("/api/v1/staff/ping", cookie(staffToken()));
        String csrf = csrfTokenTu(doTruoc);

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Cookie", "qros_session=" + staffToken() + "; XSRF-TOKEN=" + csrf);
        headers.put("X-XSRF-TOKEN", csrf);

        assertThat(post("/api/v1/staff/ping", headers).statusCode()).isEqualTo(200);
    }

    @Test
    void nfrSec05_vungQuanTriCungBatCsrf() throws Exception {
        assertThat(post("/api/v1/admin/ping", cookie(adminToken())).statusCode()).isEqualTo(403);
    }

    @Test
    void nfrSec05_vungKhachKhongCanCsrfViKhongDungCookie() throws Exception {
        // Token đi qua header Authorization, trình duyệt không tự đính kèm, nên không có bề mặt CSRF.
        HttpResponse<String> phanHoi = post("/api/v1/guest/ping",
                Map.of("Authorization", "Bearer " + guestToken()));

        assertThat(phanHoi.statusCode()).isEqualTo(200);
    }

    @Test
    void frAuth01_duongDanKhongThuocVungNaoBiTuChoiMacDinh() throws Exception {
        HttpResponse<String> phanHoi = get("/api/v1/khong-ton-tai", Map.of());

        // Từ chối, không phải 404: phần không khớp vùng nào phải đóng, kể cả khi chưa ai viết
        // controller cho nó.
        assertThat(phanHoi.statusCode()).isEqualTo(403);
        assertThat(phanHoi.headers().firstValue("Content-Type"))
                .hasValueSatisfying(value -> assertThat(value).startsWith("application/problem+json"));
    }

    @Test
    void frAuth01_duongDanDangNhapVaWebhookDiQuaDuocTangBaoMat() throws Exception {
        // Chưa có controller nào cho hai đường này (BL-M0-08 và M2), nên đi qua được tầng bảo mật
        // nghĩa là nhận 404 theo RFC 7807 chứ không phải 403.
        HttpResponse<String> dangNhap = post("/api/v1/auth/login", Map.of());
        HttpResponse<String> webhook = post("/api/v1/webhooks/payments/vnpay", Map.of());

        assertThat(dangNhap.statusCode()).isEqualTo(404);
        assertThat(webhook.statusCode()).isEqualTo(404);
        assertThat(dangNhap.body()).contains("\"code\":\"NOT_FOUND\"");
    }

    @Test
    void tmOps02_probeSucKhoeKhongDoiXacThuc() throws Exception {
        // Actuator nằm ở cổng quản trị riêng; thêm Spring Security không được biến probe thành 401,
        // nếu không thì trình điều phối coi tiến trình là chết.
        assertThat(managementGet("/actuator/health/liveness").statusCode()).isEqualTo(200);
        assertThat(managementGet("/actuator/health/readiness").body()).contains("UP");
    }

    private static Map<String, String> cookie(String token) {
        return Map.of("Cookie", "qros_session=" + token);
    }

    private static String csrfTokenTu(HttpResponse<String> phanHoi) {
        Optional<String> setCookie = phanHoi.headers().allValues("Set-Cookie").stream()
                .filter(value -> value.startsWith("XSRF-TOKEN="))
                .findFirst();
        assertThat(setCookie).as("máy chủ phải phát cookie XSRF-TOKEN cho SPA").isPresent();
        String value = setCookie.orElseThrow();
        return value.substring("XSRF-TOKEN=".length(), value.indexOf(';'));
    }
}
