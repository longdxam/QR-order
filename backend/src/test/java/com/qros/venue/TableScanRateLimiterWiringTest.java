package com.qros.venue;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.security.KeyPair;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import com.qros.integration.QrosIntegrationTest;
import com.qros.shared.id.UuidV7;
import com.qros.venue.service.TableScanRateLimiter;

/**
 * Bước 6 mục 5.3.2: xác nhận {@link TableScanRateLimiter} thật sự nằm trong chuỗi gọi của
 * {@code TableSessionService}, tách khỏi {@link TableSessionHttpFlowTest} vì phải ghi đè bean này
 * bằng một hiện thực luôn chặn — không thể dùng chung context với các test kỳ vọng đường vui vẻ.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TableScanRateLimiterWiringTest extends QrosIntegrationTest {

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void gioiHanTanSuat_chanTruocKhiChamCsdlPhien() throws Exception {
        UUID storeId = UuidV7.generate();
        jdbcTemplate.update("""
                INSERT INTO store (id, code, name, timezone, opens_at, closes_at, active)
                VALUES (?, ?, 'Chi nhánh kiểm thử', 'UTC', '00:00:00', '23:59:59', true)""",
                storeId, "RATE-" + storeId);
        UUID tableId = UuidV7.generate();
        String maBan = UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase(Locale.ROOT);
        jdbcTemplate.update("""
                INSERT INTO restaurant_table (id, store_id, label, short_code, status)
                VALUES (?, ?, 'A-01', ?, 'AVAILABLE')""",
                tableId, storeId, maBan);
        KeyPair keyPair = QrTestTokens.ed25519KeyPair();
        String kid = "qr-rate-" + UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO qr_signing_key (kid, store_id, public_key, private_key_ref, status)
                VALUES (?, ?, ?, 'test-only', 'ACTIVE')""",
                kid, storeId, QrTestTokens.rawPublicKey(keyPair));

        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("iss", "qros-venue-test");
        claims.put("typ", "QR_TABLE");
        claims.put("sid", storeId.toString());
        claims.put("tid", tableId.toString());
        claims.put("iat", System.currentTimeMillis() / 1000);
        claims.put("v", 1);
        String qrToken = QrTestTokens.signedEd25519(kid, claims, keyPair);

        String body = "{\"qrToken\":\"%s\",\"deviceId\":\"%s\"}".formatted(qrToken, UuidV7.generate());
        HttpResponse<String> phanHoi = client.send(HttpRequest.newBuilder(uri("/api/v1/guest/sessions"))
                        .header("Content-Type", "application/json")
                        .POST(BodyPublishers.ofString(body))
                        .build(),
                BodyHandlers.ofString());

        assertThat(phanHoi.statusCode()).isEqualTo(429);
        assertThat(phanHoi.body()).contains("\"code\":\"RATE_LIMITED\"");
        // Chưa chạm CSDL phiên: bước 6 chặn TRƯỚC khi tạo/tham gia phiên, đúng thứ tự PRD 5.3.2.
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM table_session WHERE table_id = ?", Integer.class, tableId))
                .isZero();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class LuonChanConfig {
        @Bean
        @Primary
        TableScanRateLimiter luonChan() {
            return (tableId, clientIp) -> {
                throw new com.qros.shared.error.QrosException(com.qros.shared.error.ErrorCode.RATE_LIMITED);
            };
        }
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}
