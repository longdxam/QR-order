package com.qros.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.security.KeyPair;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.qros.integration.QrosIntegrationTest;
import com.qros.shared.id.UuidV7;

import tools.jackson.databind.ObjectMapper;

/**
 * {@code TM-QR-01}: tự chế QR, sửa {@code sid}/{@code tid}, đổi thuật toán, hoặc dùng {@code kid}
 * lạ — lớp 1 mục 5.3.2. Mọi ca ở đây phải trả cùng một mã lỗi ({@code QR_INVALID_SIGNATURE}, một số
 * ca là {@code NOT_FOUND}/{@code 401} tuỳ tầng nào chặn trước) để không lộ QR sai ở bước nào.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class QrForgeryTest extends QrosIntegrationTest {

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final HttpClient client = HttpClient.newHttpClient();
    private UUID storeId;
    private UUID tableId;
    private KeyPair keyPair;
    private String kid;

    @BeforeEach
    void chuanBi() {
        storeId = UuidV7.generate();
        jdbcTemplate.update("""
                INSERT INTO store (id, code, name, timezone, opens_at, closes_at, active)
                VALUES (?, ?, 'Chi nhánh kiểm thử', 'UTC', '00:00:00', '23:59:59', true)""",
                storeId, "FORGE-" + storeId);
        tableId = UuidV7.generate();
        jdbcTemplate.update("""
                INSERT INTO restaurant_table (id, store_id, label, short_code, status)
                VALUES (?, ?, 'A-01', ?, 'AVAILABLE')""",
                tableId, storeId, maNgauNhien());
        keyPair = JwsTestTokens.ed25519KeyPair();
        kid = "qr-forge-" + UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO qr_signing_key (kid, store_id, public_key, private_key_ref, status)
                VALUES (?, ?, ?, 'test-only', 'ACTIVE')""",
                kid, storeId, rawPublicKey(keyPair));
    }

    @Test
    void algNone_biTuChoi() throws Exception {
        String token = JwsTestTokens.unsigned(kid, qrClaims(storeId, tableId));
        assertThat(guiQr(token).statusCode()).isEqualTo(401);
    }

    @Test
    void hmacBangKhoaCongKhai_biTuChoi() throws Exception {
        String token = JwsTestTokens.hmacWithPublicKey(kid, qrClaims(storeId, tableId), keyPair);
        assertThat(guiQr(token).statusCode()).isEqualTo(401);
    }

    @Test
    void kidLa_biTuChoi() throws Exception {
        String token = JwsTestTokens.signedEd25519("kid-khong-ton-tai", qrClaims(storeId, tableId), keyPair);
        assertThat(guiQr(token).statusCode()).isEqualTo(401);
    }

    @Test
    void chuKyBiSua_biTuChoi() throws Exception {
        String token = JwsTestTokens.signedEd25519(kid, qrClaims(storeId, tableId), keyPair);
        // Đổi một ký tự ở GIỮA đoạn chữ ký (sau dấu chấm cuối) — không phải ký tự cuối cùng: base64url
        // không đệm (without padding) khiến vài bit cuối của ký tự cuối là bit đệm không mang dữ liệu
        // thật, nên sửa đúng ký tự cuối có xác suất khác 0 không đổi được byte chữ ký nào (test từng
        // flaky vì lý do này). Ký tự giữa đoạn chữ ký luôn rơi vào phần mang dữ liệu thật.
        int batDauChuKy = token.lastIndexOf('.') + 1;
        int viTri = batDauChuKy + (token.length() - batDauChuKy) / 2;
        char kyTuMoi = token.charAt(viTri) == 'A' ? 'B' : 'A';
        String bienDang = token.substring(0, viTri) + kyTuMoi + token.substring(viTri + 1);
        assertThat(guiQr(bienDang).statusCode()).isEqualTo(401);
    }

    @Test
    void suaTidSauKhiKy_chuKySaiNenBiTuChoi() throws Exception {
        // Chữ ký ký trên toàn bộ payload gồm cả tid — sửa tid mà không ký lại thì rơi vào đúng
        // nhánh "chữ ký sai", không đi xa hơn được tới bước đọc claim.
        Map<String, Object> claims = qrClaims(storeId, tableId);
        String tokenGoc = JwsTestTokens.signedEd25519(kid, claims, keyPair);
        String[] phan = tokenGoc.split("\\.");
        Map<String, Object> claimsGia = qrClaims(storeId, UuidV7.generate());
        String phanClaimGia = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(new ObjectMapper().writeValueAsBytes(claimsGia));
        String tokenGia = phan[0] + "." + phanClaimGia + "." + phan[2];

        assertThat(guiQr(tokenGia).statusCode()).isEqualTo(401);
    }

    @Test
    void duaKhoaSangChiNhanhKhac_khongTimThayBanNenBiTuChoi() throws Exception {
        // Chữ ký hợp lệ, đúng chi nhánh sở hữu khoá — nhưng tableId lại thuộc một chi nhánh khác.
        // QrTokenVerifier qua được (chữ ký/kid/sid đều khớp khoá), TableSessionService mới là nơi
        // chặn vì bàn không thuộc chi nhánh trong token.
        UUID storeKhac = UuidV7.generate();
        jdbcTemplate.update("""
                INSERT INTO store (id, code, name, timezone, opens_at, closes_at, active)
                VALUES (?, ?, 'Chi nhánh khác', 'UTC', '00:00:00', '23:59:59', true)""",
                storeKhac, "FORGE-OTHER-" + storeKhac);
        UUID banChiNhanhKhac = UuidV7.generate();
        jdbcTemplate.update("""
                INSERT INTO restaurant_table (id, store_id, label, short_code, status)
                VALUES (?, ?, 'X-01', ?, 'AVAILABLE')""",
                banChiNhanhKhac, storeKhac, maNgauNhien());

        String token = JwsTestTokens.signedEd25519(kid, qrClaims(storeId, banChiNhanhKhac), keyPair);
        assertThat(guiQr(token).statusCode()).isEqualTo(401);
    }

    private static Map<String, Object> qrClaims(UUID storeId, UUID tableId) {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("iss", "qros-venue-test");
        claims.put("typ", "QR_TABLE");
        claims.put("sid", storeId.toString());
        claims.put("tid", tableId.toString());
        claims.put("iat", System.currentTimeMillis() / 1000);
        claims.put("v", 1);
        return claims;
    }

    private static byte[] rawPublicKey(KeyPair keyPair) {
        byte[] encoded = keyPair.getPublic().getEncoded();
        byte[] raw = new byte[32];
        System.arraycopy(encoded, encoded.length - 32, raw, 0, 32);
        return raw;
    }

    private static String maNgauNhien() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase(Locale.ROOT);
    }

    private HttpResponse<String> guiQr(String qrToken) throws Exception {
        String body = "{\"qrToken\":\"%s\",\"deviceId\":\"%s\"}".formatted(qrToken, UuidV7.generate());
        return client.send(HttpRequest.newBuilder(uri("/api/v1/guest/sessions"))
                        .header("Content-Type", "application/json")
                        .POST(BodyPublishers.ofString(body))
                        .build(),
                BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}
