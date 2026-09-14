package com.qros.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.security.KeyPair;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.qros.integration.QrosIntegrationTest;
import com.qros.shared.id.UuidV7;
import com.qros.shared.security.Totp;

/**
 * {@code TM-QR-02}: dùng lại ảnh QR từ xa hoặc mã TOTP cũ để mở phiên. Hai trong ba mitigation ghi
 * ở threat model kiểm được ở đây (giờ mở cửa, TOTP ±1 bước); "đơn đầu chờ duyệt" thuộc module
 * {@code ordering} chưa tồn tại (M1-03) — trạng thái vẫn là "Một phần" cho tới khi thẻ đó xong,
 * không giả vờ đã đóng trọn mối đe doạ này.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class QrReplayTest extends QrosIntegrationTest {

    private static final int OTP_STEP_SECONDS = 60;

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void ngoaiGioMoCua_traStoreClosed403() throws Exception {
        Instant now = Instant.now();
        // Khung giờ mở cửa 5 phút trong tương lai xa — "bây giờ" chắc chắn nằm ngoài.
        String moLuc = now.plus(Duration.ofHours(3)).toString().substring(11, 19);
        String dongLuc = now.plus(Duration.ofHours(3)).plusSeconds(300).toString().substring(11, 19);

        Setup setup = seed(moLuc, dongLuc, false, null);
        String qrToken = kyQr(setup, qrClaims(setup.storeId(), setup.tableId(), null));

        HttpResponse<String> phanHoi = guiQr(qrToken);
        assertThat(phanHoi.statusCode()).isEqualTo(403);
        assertThat(phanHoi.body()).contains("\"code\":\"STORE_CLOSED\"");
    }

    @Test
    void totpCuHonNguongTroi_bTuChoi() throws Exception {
        byte[] secret = Totp.generateSecret();
        Setup setup = seed("00:00:00", "23:59:59", true, Base64.getEncoder().encodeToString(secret));

        // Mã hợp lệ tại một thời điểm CÁCH ĐÂY XA (giả lập ảnh QR/mã chụp từ trước rồi dùng lại
        // sau khi đã trôi khỏi dung sai ±1 bước = ngoài ±60 giây quanh bước hiện hành).
        String maCu = Totp.currentCode(secret, Instant.now().minus(Duration.ofMinutes(10)), OTP_STEP_SECONDS);
        String qrToken = kyQr(setup, qrClaims(setup.storeId(), setup.tableId(), maCu));

        HttpResponse<String> phanHoi = guiQr(qrToken);
        assertThat(phanHoi.statusCode()).isEqualTo(401);
        assertThat(phanHoi.body()).contains("\"code\":\"QR_OTP_EXPIRED\"");
    }

    @Test
    void totpTrongDungSaiMotBuoc_vanChapNhan() throws Exception {
        byte[] secret = Totp.generateSecret();
        Setup setup = seed("00:00:00", "23:59:59", true, Base64.getEncoder().encodeToString(secret));

        // Lệch đúng một bước (60 giây) — vẫn trong dung sai ±1 theo PRD ("chấp nhận lệch ±1 bước").
        String maLechMotBuoc = Totp.currentCode(secret,
                Instant.now().minusSeconds(OTP_STEP_SECONDS), OTP_STEP_SECONDS);
        String qrToken = kyQr(setup, qrClaims(setup.storeId(), setup.tableId(), maLechMotBuoc));

        assertThat(guiQr(qrToken).statusCode()).isEqualTo(201);
    }

    private Setup seed(String opensAt, String closesAt, boolean rotatingQr, String totpSecretBase64) {
        UUID storeId = UuidV7.generate();
        jdbcTemplate.update("""
                INSERT INTO store (id, code, name, timezone, opens_at, closes_at, active)
                VALUES (?, ?, 'Chi nhánh kiểm thử', 'UTC', ?::time, ?::time, true)""",
                storeId, "REPLAY-" + storeId, opensAt, closesAt);
        UUID tableId = UuidV7.generate();
        String maBan = UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase(Locale.ROOT);
        jdbcTemplate.update("""
                INSERT INTO restaurant_table
                    (id, store_id, label, short_code, status, rotating_qr_enabled, totp_secret_ref)
                VALUES (?, ?, 'A-01', ?, 'AVAILABLE', ?, ?)""",
                tableId, storeId, maBan, rotatingQr, totpSecretBase64);
        KeyPair keyPair = JwsTestTokens.ed25519KeyPair();
        String kid = "qr-replay-" + UUID.randomUUID();
        byte[] raw = new byte[32];
        System.arraycopy(keyPair.getPublic().getEncoded(), keyPair.getPublic().getEncoded().length - 32, raw, 0, 32);
        jdbcTemplate.update("""
                INSERT INTO qr_signing_key (kid, store_id, public_key, private_key_ref, status)
                VALUES (?, ?, ?, 'test-only', 'ACTIVE')""",
                kid, storeId, raw);
        return new Setup(storeId, tableId, kid, keyPair);
    }

    private static Map<String, Object> qrClaims(UUID storeId, UUID tableId, String otp) {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("iss", "qros-venue-test");
        claims.put("typ", "QR_TABLE");
        claims.put("sid", storeId.toString());
        claims.put("tid", tableId.toString());
        claims.put("iat", System.currentTimeMillis() / 1000);
        claims.put("v", 1);
        if (otp != null) {
            claims.put("otp", otp);
        }
        return claims;
    }

    private static String kyQr(Setup setup, Map<String, Object> claims) {
        return JwsTestTokens.signedEd25519(setup.kid(), claims, setup.keyPair());
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

    private record Setup(UUID storeId, UUID tableId, String kid, KeyPair keyPair) {
    }
}
