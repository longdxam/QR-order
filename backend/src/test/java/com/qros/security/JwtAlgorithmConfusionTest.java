package com.qros.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * {@code TM-AUTH-02}: thuật toán, audience và khoá chéo vùng đều bị từ chối
 * ({@code NFR-SEC-02}, {@code NFR-SEC-03}, {@code NFR-SEC-08}).
 *
 * <p>Mọi ca ở đây đi qua HTTP thật để kiểm chính thứ chạy trong sản xuất — chuỗi filter, chứ không
 * phải một bộ xác minh gọi trực tiếp.
 */
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class JwtAlgorithmConfusionTest extends SecurityZoneTestSupport {

    @Test
    void nfrSec02_tokenDungVungThiDiQua() throws Exception {
        assertThat(get("/api/v1/guest/ping", bearer(guestToken())).statusCode()).isEqualTo(200);
        assertThat(get("/api/v1/staff/ping", cookie(staffToken())).statusCode()).isEqualTo(200);
        assertThat(get("/api/v1/admin/ping", cookie(adminToken())).statusCode()).isEqualTo(200);
    }

    @Test
    void tmAuth02_algNoneBiTuChoi() throws Exception {
        String token = JwsTestTokens.unsigned(GUEST_KID,
                JwsTestTokens.claims(ISSUER, "qros-guest", "table_session", Instant.now().plusSeconds(900)));

        assertThat(get("/api/v1/guest/ping", bearer(token)).statusCode()).isEqualTo(401);
    }

    @Test
    void tmAuth02_danhTraoSangHmacBangKhoaCongKhaiBiTuChoi() throws Exception {
        // Khoá công khai được công bố công khai (NFR-SEC-07), nên nếu bộ xác minh đọc alg từ token
        // thì kẻ tấn công tự ký được token hợp lệ bằng chính khoá đó.
        String token = JwsTestTokens.hmacWithPublicKey(GUEST_KID,
                JwsTestTokens.claims(ISSUER, "qros-guest", "table_session", Instant.now().plusSeconds(900)),
                GUEST_KEYS);

        assertThat(get("/api/v1/guest/ping", bearer(token)).statusCode()).isEqualTo(401);
    }

    @Test
    void tmAuth02_tokenKhachKhongDiQuaVungNhanVienVaQuanTri() throws Exception {
        String token = guestToken();

        assertThat(get("/api/v1/staff/ping", cookie(token)).statusCode()).isEqualTo(401);
        assertThat(get("/api/v1/admin/ping", cookie(token)).statusCode()).isEqualTo(401);
        // Cả khi gửi kèm header thay vì cookie: vùng nhân viên không nhận token từ header.
        assertThat(get("/api/v1/staff/ping", bearer(token)).statusCode()).isEqualTo(401);
    }

    @Test
    void tmAuth02_tokenNhanVienKhongDiQuaVungKhachVaQuanTri() throws Exception {
        assertThat(get("/api/v1/guest/ping", bearer(staffToken())).statusCode()).isEqualTo(401);
        assertThat(get("/api/v1/admin/ping", cookie(staffToken())).statusCode()).isEqualTo(401);
    }

    @Test
    void tmAuth02_kyDungKhoaNhungSaiAudienceBiTuChoi() throws Exception {
        // Cùng khoá vùng khách, chỉ khác audience: hàng rào thứ hai phải tự đứng vững.
        String token = JwsTestTokens.signedEd25519(GUEST_KID,
                JwsTestTokens.claims(ISSUER, "qros-staff", "table_session", Instant.now().plusSeconds(900)),
                GUEST_KEYS);

        assertThat(get("/api/v1/guest/ping", bearer(token)).statusCode()).isEqualTo(401);
    }

    @Test
    void tmAuth02_saiIssuerHoacSaiKidBiTuChoi() throws Exception {
        String saiIssuer = JwsTestTokens.signedEd25519(GUEST_KID,
                JwsTestTokens.claims("https://ke-tan-cong.example", "qros-guest", "table_session",
                        Instant.now().plusSeconds(900)), GUEST_KEYS);
        String saiKid = JwsTestTokens.signedEd25519("khong-ton-tai",
                JwsTestTokens.claims(ISSUER, "qros-guest", "table_session", Instant.now().plusSeconds(900)),
                GUEST_KEYS);

        assertThat(get("/api/v1/guest/ping", bearer(saiIssuer)).statusCode()).isEqualTo(401);
        assertThat(get("/api/v1/guest/ping", bearer(saiKid)).statusCode()).isEqualTo(401);
    }

    @Test
    void tmAuth02_chuKyBiSuaBiTuChoi() throws Exception {
        String token = guestToken();
        String daSua = token.substring(0, token.length() - 4) + "AAAA";

        assertThat(get("/api/v1/guest/ping", bearer(daSua)).statusCode()).isEqualTo(401);
    }

    @Test
    void nfrSec08_hetHanBiTuChoiNhungDungSai60GiayVanDiQua() throws Exception {
        String hetHanLau = guestToken(Instant.now().minusSeconds(300));
        String vuaHetHan = guestToken(Instant.now().minusSeconds(30));

        assertThat(get("/api/v1/guest/ping", bearer(hetHanLau)).statusCode()).isEqualTo(401);
        // Dung sai 60 giây cho lệch đồng hồ giữa máy phát và máy xác minh.
        assertThat(get("/api/v1/guest/ping", bearer(vuaHetHan)).statusCode()).isEqualTo(200);
    }

    @Test
    void tmAuth02_thieuTokenTraVeRfc7807() throws Exception {
        var phanHoi = get("/api/v1/guest/ping", Map.of());

        assertThat(phanHoi.statusCode()).isEqualTo(401);
        assertThat(phanHoi.headers().firstValue("Content-Type"))
                .hasValueSatisfying(value -> assertThat(value).startsWith("application/problem+json"));
        assertThat(phanHoi.body()).contains("\"code\":\"UNAUTHENTICATED\"").contains("traceId");
        // Tiêu đề tiếng Việt còn nguyên dấu: chốt luôn charset của phản hồi, vì entry point tự ghi
        // thẳng ra response và mặc định của servlet là ISO-8859-1.
        assertThat(phanHoi.body()).contains("Cần đăng nhập");
        // Không có manh mối nào về việc token hỏng ở bước nào.
        assertThat(phanHoi.body()).doesNotContain("kid").doesNotContain("audience")
                .doesNotContain("Exception");
    }

    private static Map<String, String> bearer(String token) {
        return Map.of("Authorization", "Bearer " + token);
    }

    private static Map<String, String> cookie(String token) {
        return Map.of("Cookie", "qros_session=" + token);
    }
}
