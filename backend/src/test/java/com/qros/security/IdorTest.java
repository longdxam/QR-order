package com.qros.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.qros.integration.QrosIntegrationTest;
import com.qros.shared.id.UuidV7;
import com.qros.shared.security.JwtIssuer;

/**
 * {@code TM-ACC-01}: UUID của đơn không phải là quyền truy cập. Mọi truy vấn đơn vùng khách phải
 * ràng buộc {@code session_id} trong câu truy vấn và trả 404 khi đơn thuộc một phiên bàn khác.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class IdorTest extends QrosIntegrationTest {

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private JwtIssuer guestJwtIssuer;

    private final HttpClient client = HttpClient.newHttpClient();
    private UUID storeId;
    private UUID orderCuaBanA;
    private String tokenBanB;

    @BeforeEach
    void chuanBi() {
        storeId = UuidV7.generate();
        jdbc.update("""
                INSERT INTO store (id, code, name, timezone, opens_at, closes_at, active)
                VALUES (?, ?, 'Quán kiểm thử IDOR', 'UTC', '00:00:00', '23:59:59', true)""",
                storeId, "IDOR-" + storeId);

        UUID tableA = themBan("A-01");
        UUID tableB = themBan("A-02");
        UUID sessionA = themPhien(tableA);
        UUID sessionB = themPhien(tableB);
        orderCuaBanA = themDon(tableA, sessionA, "IDOR-A");
        themDon(tableB, sessionB, "IDOR-B");
        tokenBanB = guestJwtIssuer.issue(sessionB.toString(), Duration.ofMinutes(90), Map.of(
                "scope", "table_session", "sid", storeId.toString(), "tid", tableB.toString(), "tv", 0));
    }

    @AfterEach
    void donDep() {
        jdbc.update("DELETE FROM order_status_log WHERE order_id IN (SELECT id FROM customer_order WHERE store_id = ?)",
                storeId);
        jdbc.update("DELETE FROM order_line_option WHERE order_line_id IN (SELECT id FROM order_line WHERE order_id IN "
                + "(SELECT id FROM customer_order WHERE store_id = ?))", storeId);
        jdbc.update("DELETE FROM order_line WHERE order_id IN (SELECT id FROM customer_order WHERE store_id = ?)", storeId);
        jdbc.update("DELETE FROM customer_order WHERE store_id = ?", storeId);
        jdbc.update("DELETE FROM session_device WHERE session_id IN (SELECT id FROM table_session WHERE store_id = ?)", storeId);
        jdbc.update("DELETE FROM table_session WHERE store_id = ?", storeId);
        jdbc.update("DELETE FROM restaurant_table WHERE store_id = ?", storeId);
        jdbc.update("DELETE FROM store WHERE id = ?", storeId);
    }

    @Test
    void docDonCuaPhienKhac_tra404_khongLoThongTin() throws Exception {
        HttpResponse<String> response = request("/api/v1/guest/orders/" + orderCuaBanA, "GET");

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).contains("\"code\":\"NOT_FOUND\"")
                // RFC 7807 phải phản chiếu URL đã gọi trong `instance`; thứ không được lộ là dữ liệu đơn.
                .doesNotContain("IDOR-A").doesNotContain("\"status\":\"PENDING\"");
    }

    @Test
    void huyDonCuaPhienKhac_tra404_vaKhongThayDoiDon() throws Exception {
        HttpResponse<String> response = request("/api/v1/guest/orders/" + orderCuaBanA + "/cancel", "POST");

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).contains("\"code\":\"NOT_FOUND\"");
        assertThat(jdbc.queryForObject("SELECT status FROM customer_order WHERE id = ?", String.class, orderCuaBanA))
                .isEqualTo("PENDING");
    }

    private UUID themBan(String label) {
        UUID id = UuidV7.generate();
        String shortCode = UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase(Locale.ROOT);
        jdbc.update("INSERT INTO restaurant_table (id, store_id, label, short_code, status) VALUES (?,?,?,?, 'OCCUPIED')",
                id, storeId, label, shortCode);
        return id;
    }

    private UUID themPhien(UUID tableId) {
        UUID id = UuidV7.generate();
        jdbc.update("""
                INSERT INTO table_session (id, store_id, table_id, status, opened_at, last_activity_at, expires_at)
                VALUES (?, ?, ?, 'OPEN', now(), now(), now() + interval '90 minutes')""", id, storeId, tableId);
        return id;
    }

    private UUID themDon(UUID tableId, UUID sessionId, String shortCode) {
        UUID id = UuidV7.generate();
        jdbc.update("""
                INSERT INTO customer_order
                    (id, store_id, table_id, session_id, short_code, status, subtotal_amount, total_amount, placed_at)
                VALUES (?, ?, ?, ?, ?, 'PENDING', 0, 0, now())""", id, storeId, tableId, sessionId, shortCode);
        return id;
    }

    private HttpResponse<String> request(String path, String method) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Authorization", "Bearer " + tokenBanB);
        return client.send("GET".equals(method) ? builder.GET().build() : builder.POST(HttpRequest.BodyPublishers.noBody()).build(),
                BodyHandlers.ofString());
    }
}
