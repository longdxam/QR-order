package com.qros.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
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
 * {@code TM-ORD-01}, {@code ADR-06}, bất biến số 1: client không bao giờ gửi giá được, dù cố tình
 * chèn trường giá hay cố thao túng giá qua đường khác. Máy chủ luôn tính lại từ catalog.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PriceTamperingTest extends QrosIntegrationTest {

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtIssuer guestJwtIssuer;

    private final HttpClient client = HttpClient.newHttpClient();
    private UUID storeId;
    private UUID itemId;
    private UUID variantId;
    private String token;

    @BeforeEach
    void chuanBi() {
        storeId = UuidV7.generate();
        jdbcTemplate.update("""
                INSERT INTO store (id, code, name, timezone, opens_at, closes_at, active)
                VALUES (?, ?, 'Quán kiểm thử giá', 'UTC', '00:00:00', '23:59:59', true)""",
                storeId, "TAMPER-" + storeId);
        UUID tableId = UuidV7.generate();
        jdbcTemplate.update("""
                INSERT INTO restaurant_table (id, store_id, label, short_code, status)
                VALUES (?, ?, 'A-04', ?, 'AVAILABLE')""",
                tableId, storeId, maNgauNhien());
        UUID sessionId = UuidV7.generate();
        jdbcTemplate.update("""
                INSERT INTO table_session (id, store_id, table_id, status, opened_at, last_activity_at, expires_at)
                VALUES (?, ?, ?, 'OPEN', now(), now(), now() + interval '90 minutes')""",
                sessionId, storeId, tableId);
        UUID categoryId = UuidV7.generate();
        jdbcTemplate.update("INSERT INTO category (id, store_id, name, display_order, active) VALUES (?,?,?,0,true)",
                categoryId, storeId, "Trà sữa");
        itemId = UuidV7.generate();
        jdbcTemplate.update("""
                INSERT INTO menu_item
                    (id, store_id, category_id, name, station, allergens, attributes, published, manually_disabled, display_order)
                VALUES (?, ?, ?, 'Trà sữa trân châu size L', 'TEA', '{}', '{}', true, false, 0)""",
                itemId, storeId, categoryId);
        variantId = UuidV7.generate();
        // Giá niêm yết đúng như kịch bản Gherkin của PRD (FR-CUS-08): 55.000 ₫.
        jdbcTemplate.update("""
                INSERT INTO menu_variant (id, menu_item_id, name, price_amount, display_order, active)
                VALUES (?, ?, 'Size L', 55000, 0, true)""",
                variantId, itemId);

        token = guestJwtIssuer.issue(sessionId.toString(), Duration.ofMinutes(90), Map.of(
                "scope", "table_session", "sid", storeId.toString(), "tid", tableId.toString(), "tv", 0));
    }

    @AfterEach
    void donDep() {
        jdbcTemplate.update("DELETE FROM order_line_option WHERE order_line_id IN "
                + "(SELECT id FROM order_line WHERE order_id IN "
                + "(SELECT id FROM customer_order WHERE store_id = ?))", storeId);
        jdbcTemplate.update("DELETE FROM order_status_log WHERE order_id IN "
                + "(SELECT id FROM customer_order WHERE store_id = ?)", storeId);
        jdbcTemplate.update("DELETE FROM order_line WHERE order_id IN "
                + "(SELECT id FROM customer_order WHERE store_id = ?)", storeId);
        jdbcTemplate.update("DELETE FROM customer_order WHERE store_id = ?", storeId);
        jdbcTemplate.update("DELETE FROM session_device WHERE session_id IN "
                + "(SELECT id FROM table_session WHERE store_id = ?)", storeId);
        jdbcTemplate.update("DELETE FROM table_session WHERE store_id = ?", storeId);
        jdbcTemplate.update("DELETE FROM menu_variant WHERE menu_item_id IN "
                + "(SELECT id FROM menu_item WHERE store_id = ?)", storeId);
        jdbcTemplate.update("DELETE FROM menu_item WHERE store_id = ?", storeId);
        jdbcTemplate.update("DELETE FROM category WHERE store_id = ?", storeId);
        jdbcTemplate.update("DELETE FROM restaurant_table WHERE store_id = ?", storeId);
        jdbcTemplate.update("DELETE FROM store WHERE id = ?", storeId);
    }

    /** Kịch bản Gherkin đúng nguyên văn PRD `FR-CUS-08`: gửi {@code unitPrice} giả trong dòng đơn. */
    @Test
    void frCus08_guiUnitPriceGia_tra400PriceNotAccepted_khongTaoDon() throws Exception {
        String body = """
                {"lines":[{"menuItemId":"%s","variantId":"%s","quantity":1,"unitPrice":1000}]}"""
                .formatted(itemId, variantId);

        HttpResponse<String> phanHoi = guiDatMon(body);

        assertThat(phanHoi.statusCode()).isEqualTo(400);
        assertThat(phanHoi.body()).contains("\"code\":\"PRICE_NOT_ACCEPTED\"");
        assertThat(soDonHienCo()).isZero();
    }

    @Test
    void guiTotalAmountGia_tra400PriceNotAccepted() throws Exception {
        String body = """
                {"lines":[{"menuItemId":"%s","variantId":"%s","quantity":1}],"totalAmount":1}"""
                .formatted(itemId, variantId);

        HttpResponse<String> phanHoi = guiDatMon(body);
        assertThat(phanHoi.statusCode()).isEqualTo(400);
        assertThat(phanHoi.body()).contains("\"code\":\"PRICE_NOT_ACCEPTED\"");
    }

    @Test
    void guiLineTotalGiaOMotDong_tra400PriceNotAccepted() throws Exception {
        String body = """
                {"lines":[{"menuItemId":"%s","variantId":"%s","quantity":1,"lineTotal":1}]}"""
                .formatted(itemId, variantId);

        HttpResponse<String> phanHoi = guiDatMon(body);
        assertThat(phanHoi.statusCode()).isEqualTo(400);
        assertThat(phanHoi.body()).contains("\"code\":\"PRICE_NOT_ACCEPTED\"");
    }

    @Test
    void guiTruongLaKhongLienQuanGia_van400_nhungLaValidationFailed() throws Exception {
        String body = """
                {"lines":[{"menuItemId":"%s","variantId":"%s","quantity":1,"mauSac":"do"}]}"""
                .formatted(itemId, variantId);

        HttpResponse<String> phanHoi = guiDatMon(body);

        assertThat(phanHoi.statusCode()).isEqualTo(400);
        // Trường lạ nhưng KHÔNG phải giá — phải phân biệt được với PRICE_NOT_ACCEPTED, không lẫn lộn.
        assertThat(phanHoi.body()).contains("\"code\":\"VALIDATION_FAILED\"");
    }

    /**
     * {@code ADR-06}: dù không gửi giá, tổng tiền luôn do máy chủ tính từ catalog — đặt món hợp lệ
     * rồi xác nhận giá trả về đúng {@code price_amount} trong CSDL, không phải một giá trị nào
     * client có thể ảnh hưởng qua {@code quantity} hay bất kỳ trường nào khác ngoài số lượng.
     */
    @Test
    void datMonHopLe_giaTraVeLuonLaGiaCatalog_khongPhuThuocClient() throws Exception {
        String body = """
                {"lines":[{"menuItemId":"%s","variantId":"%s","quantity":3}]}"""
                .formatted(itemId, variantId);

        HttpResponse<String> phanHoi = guiDatMon(body);

        assertThat(phanHoi.statusCode()).isEqualTo(201);
        // 55.000 × 3 = 165.000 — đúng bằng giá niêm yết nhân số lượng, không có cách nào khác client
        // có thể tác động tới con số này.
        assertThat(phanHoi.body()).contains("\"amount\":165000");
    }

    private HttpResponse<String> guiDatMon(String body) throws Exception {
        return client.send(HttpRequest.newBuilder(uri("/api/v1/guest/orders"))
                        .header("Content-Type", "application/json")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .header("Authorization", "Bearer " + token)
                        .POST(BodyPublishers.ofString(body))
                        .build(),
                BodyHandlers.ofString());
    }

    private int soDonHienCo() {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM customer_order WHERE store_id = ?", Integer.class, storeId);
    }

    private static String maNgauNhien() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase(Locale.ROOT);
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}
