package com.qros.ordering;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
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
 * {@code FR-CUS-08}, {@code FR-CUS-09}, {@code ADR-06}: đặt món qua HTTP thật — điểm nhạy cảm nhất
 * của toàn bộ hợp đồng. Tấn công giá (trường giá bị từ chối) nằm riêng ở
 * {@code security/PriceTamperingTest} (`TM-ORD-01`); ở đây tập trung luồng nghiệp vụ đúng/gần-đúng.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GuestOrderHttpFlowTest extends QrosIntegrationTest {

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtIssuer guestJwtIssuer;

    private final HttpClient client = HttpClient.newHttpClient();
    private final List<UUID> storeIdsDaTao = new ArrayList<>();

    private UUID storeId;
    private UUID tableId;
    private UUID sessionId;
    private UUID categoryId;
    private UUID itemId;
    private UUID variantId;
    private UUID optionGroupId;
    private UUID optionChoiceId;
    private String token;

    @BeforeEach
    void chuanBi() {
        storeId = UuidV7.generate();
        storeIdsDaTao.add(storeId);
        jdbcTemplate.update("""
                INSERT INTO store (id, code, name, timezone, opens_at, closes_at, active)
                VALUES (?, ?, 'Quán kiểm thử đặt món', 'UTC', '00:00:00', '23:59:59', true)""",
                storeId, "ORDER-" + storeId);

        tableId = UuidV7.generate();
        jdbcTemplate.update("""
                INSERT INTO restaurant_table (id, store_id, label, short_code, status)
                VALUES (?, ?, 'A-01', ?, 'AVAILABLE')""",
                tableId, storeId, maNgauNhien());

        sessionId = UuidV7.generate();
        // now()/interval ngay trong SQL — tránh bind tham số Instant/Timestamp (JdbcTemplate không
        // tự suy ra kiểu SQL cho java.time.Instant, và java.sql.* bị ArchUnit cấm toàn repo).
        jdbcTemplate.update("""
                INSERT INTO table_session (id, store_id, table_id, status, opened_at, last_activity_at, expires_at)
                VALUES (?, ?, ?, 'OPEN', now(), now(), now() + interval '90 minutes')""",
                sessionId, storeId, tableId);

        categoryId = UuidV7.generate();
        jdbcTemplate.update("INSERT INTO category (id, store_id, name, display_order, active) VALUES (?,?,?,0,true)",
                categoryId, storeId, "Trà sữa");

        itemId = UuidV7.generate();
        jdbcTemplate.update("""
                INSERT INTO menu_item
                    (id, store_id, category_id, name, station, allergens, attributes, published, manually_disabled, display_order)
                VALUES (?, ?, ?, 'Trà sữa trân châu', 'TEA', '{}', '{}', true, false, 0)""",
                itemId, storeId, categoryId);

        variantId = UuidV7.generate();
        jdbcTemplate.update("""
                INSERT INTO menu_variant (id, menu_item_id, name, price_amount, display_order, active)
                VALUES (?, ?, 'Size M', 45000, 0, true)""",
                variantId, itemId);

        optionGroupId = UuidV7.generate();
        jdbcTemplate.update("""
                INSERT INTO option_group (id, store_id, name, selection, is_required, min_select, max_select)
                VALUES (?, ?, 'Mức đường', 'SINGLE', false, 0, 1)""",
                optionGroupId, storeId);
        optionChoiceId = UuidV7.generate();
        jdbcTemplate.update("""
                INSERT INTO option_choice (id, option_group_id, name, surcharge_amount, display_order, active)
                VALUES (?, ?, '100%', 5000, 0, true)""",
                optionChoiceId, optionGroupId);
        jdbcTemplate.update("""
                INSERT INTO menu_item_option_group (menu_item_id, option_group_id, display_order)
                VALUES (?, ?, 0)""",
                itemId, optionGroupId);

        token = guestJwtIssuer.issue(sessionId.toString(), Duration.ofMinutes(90), Map.of(
                "scope", "table_session", "sid", storeId.toString(), "tid", tableId.toString(), "tv", 0));
    }

    @AfterEach
    void donDep() {
        for (UUID id : storeIdsDaTao) {
            jdbcTemplate.update("DELETE FROM order_status_log WHERE order_id IN "
                    + "(SELECT id FROM customer_order WHERE store_id = ?)", id);
            jdbcTemplate.update("DELETE FROM order_line_option WHERE order_line_id IN "
                    + "(SELECT id FROM order_line WHERE order_id IN "
                    + "(SELECT id FROM customer_order WHERE store_id = ?))", id);
            jdbcTemplate.update("DELETE FROM order_line WHERE order_id IN "
                    + "(SELECT id FROM customer_order WHERE store_id = ?)", id);
            jdbcTemplate.update("DELETE FROM customer_order WHERE store_id = ?", id);
            jdbcTemplate.update("DELETE FROM staff_call WHERE session_id IN "
                    + "(SELECT id FROM table_session WHERE store_id = ?)", id);
            jdbcTemplate.update("DELETE FROM session_device WHERE session_id IN "
                    + "(SELECT id FROM table_session WHERE store_id = ?)", id);
            jdbcTemplate.update("DELETE FROM table_session WHERE store_id = ?", id);
            jdbcTemplate.update("DELETE FROM menu_item_option_group WHERE menu_item_id IN "
                    + "(SELECT id FROM menu_item WHERE store_id = ?)", id);
            jdbcTemplate.update("DELETE FROM option_choice WHERE option_group_id IN "
                    + "(SELECT id FROM option_group WHERE store_id = ?)", id);
            jdbcTemplate.update("DELETE FROM option_group WHERE store_id = ?", id);
            jdbcTemplate.update("DELETE FROM recipe_component WHERE menu_variant_id IN "
                    + "(SELECT id FROM menu_variant WHERE menu_item_id IN "
                    + "(SELECT id FROM menu_item WHERE store_id = ?))", id);
            jdbcTemplate.update("DELETE FROM menu_variant WHERE menu_item_id IN "
                    + "(SELECT id FROM menu_item WHERE store_id = ?)", id);
            jdbcTemplate.update("DELETE FROM menu_item WHERE store_id = ?", id);
            jdbcTemplate.update("DELETE FROM category WHERE store_id = ?", id);
            jdbcTemplate.update("DELETE FROM ingredient WHERE store_id = ?", id);
            jdbcTemplate.update("DELETE FROM restaurant_table WHERE store_id = ?", id);
            jdbcTemplate.update("DELETE FROM store WHERE id = ?", id);
        }
    }

    @Test
    void datMonThanhCong_giaTinhTuCatalog_laDonDauNenChoXacNhan() throws Exception {
        HttpResponse<String> phanHoi = datMon(UUID.randomUUID(), donGianDon());

        assertThat(phanHoi.statusCode()).isEqualTo(201);
        assertThat(phanHoi.body()).contains("\"amount\":50000") // 45000 + 5000 phụ phí
                .contains("\"requiresStaffConfirmation\":true")
                .contains("\"status\":\"PENDING\"");
        assertThat(phanHoi.headers().firstValue("Location")).isPresent();
    }

    @Test
    void donThuHai_khongCanXacNhanNua() throws Exception {
        datMon(UUID.randomUUID(), donGianDon());
        HttpResponse<String> donHai = datMon(UUID.randomUUID(), donGianDon());

        assertThat(donHai.statusCode()).isEqualTo(201);
        assertThat(donHai.body()).contains("\"requiresStaffConfirmation\":false");
    }

    @Test
    void guiLaiCungKhoa_traLaiDonCu_khongTaoDonMoi() throws Exception {
        UUID key = UUID.randomUUID();
        HttpResponse<String> lanDau = datMon(key, donGianDon());
        HttpResponse<String> lanHai = datMon(key, donGianDon());

        assertThat(lanDau.statusCode()).isEqualTo(201);
        assertThat(lanHai.statusCode()).isEqualTo(200);
        String idDau = layTruong(lanDau.body(), "id");
        String idHai = layTruong(lanHai.body(), "id");
        assertThat(idHai).isEqualTo(idDau);
        assertThat((long) jdbcTemplate.queryForObject(
                "SELECT count(*) FROM customer_order WHERE store_id = ?", Long.class, storeId))
                .isEqualTo(1);
    }

    @Test
    void cungKhoaKhacNoiDung_tra422() throws Exception {
        UUID key = UUID.randomUUID();
        datMon(key, donGianDon());
        String noiDungKhac = "{\"lines\":[{\"menuItemId\":\"%s\",\"variantId\":\"%s\",\"quantity\":5}]}"
                .formatted(itemId, variantId);

        HttpResponse<String> phanHoi = client.send(HttpRequest.newBuilder(uri("/api/v1/guest/orders"))
                        .header("Content-Type", "application/json")
                        .header("Idempotency-Key", key.toString())
                        .header("Authorization", "Bearer " + token)
                        .POST(BodyPublishers.ofString(noiDungKhac))
                        .build(),
                BodyHandlers.ofString());

        assertThat(phanHoi.statusCode()).isEqualTo(422);
        assertThat(phanHoi.body()).contains("\"code\":\"IDEMPOTENCY_KEY_REUSED\"");
    }

    @Test
    void monHetHang_tra409() throws Exception {
        UUID ingredientId = UuidV7.generate();
        jdbcTemplate.update("INSERT INTO ingredient (id, store_id, name, unit, sold_out) VALUES (?,?,?,?,true)",
                ingredientId, storeId, "Trân châu đen", "kg");
        jdbcTemplate.update("INSERT INTO recipe_component (menu_variant_id, ingredient_id, quantity) VALUES (?,?,0.05)",
                variantId, ingredientId);

        HttpResponse<String> phanHoi = datMon(UUID.randomUUID(), donGianDon());

        assertThat(phanHoi.statusCode()).isEqualTo(409);
        assertThat(phanHoi.body()).contains("\"code\":\"ITEM_SOLD_OUT\"");
    }

    @Test
    void tuyChonKhongThuocMon_traValidationFailed() throws Exception {
        UUID tuyChonLa = UuidV7.generate();
        String body = "{\"lines\":[{\"menuItemId\":\"%s\",\"variantId\":\"%s\",\"optionIds\":[\"%s\"],\"quantity\":1}]}"
                .formatted(itemId, variantId, tuyChonLa);

        HttpResponse<String> phanHoi = client.send(HttpRequest.newBuilder(uri("/api/v1/guest/orders"))
                        .header("Content-Type", "application/json")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .header("Authorization", "Bearer " + token)
                        .POST(BodyPublishers.ofString(body))
                        .build(),
                BodyHandlers.ofString());

        assertThat(phanHoi.statusCode()).isEqualTo(400);
        assertThat(phanHoi.body()).contains("\"code\":\"VALIDATION_FAILED\"");
    }

    @Test
    void xemChiTietDon_dungPhien_thanhCong_saiPhien_tra404() throws Exception {
        HttpResponse<String> datDon = datMon(UUID.randomUUID(), donGianDon());
        String orderId = layTruong(datDon.body(), "id");

        HttpResponse<String> xemDung = client.send(HttpRequest.newBuilder(uri("/api/v1/guest/orders/" + orderId))
                        .header("Authorization", "Bearer " + token)
                        .GET().build(),
                BodyHandlers.ofString());
        assertThat(xemDung.statusCode()).isEqualTo(200);

        String tokenPhienKhac = guestJwtIssuer.issue(UuidV7.generate().toString(), Duration.ofMinutes(90),
                Map.of("scope", "table_session", "sid", storeId.toString(), "tid", tableId.toString(), "tv", 0));
        HttpResponse<String> xemSai = client.send(HttpRequest.newBuilder(uri("/api/v1/guest/orders/" + orderId))
                        .header("Authorization", "Bearer " + tokenPhienKhac)
                        .GET().build(),
                BodyHandlers.ofString());
        assertThat(xemSai.statusCode()).isEqualTo(404);
    }

    @Test
    void danhSachDonTrongPhien_tongDungTong() throws Exception {
        datMon(UUID.randomUUID(), donGianDon());
        datMon(UUID.randomUUID(), donGianDon());

        HttpResponse<String> phanHoi = client.send(HttpRequest.newBuilder(uri("/api/v1/guest/orders"))
                        .header("Authorization", "Bearer " + token)
                        .GET().build(),
                BodyHandlers.ofString());

        assertThat(phanHoi.statusCode()).isEqualTo(200);
        assertThat(phanHoi.body()).contains("\"sessionTotal\":{\"amount\":100000");
    }

    @Test
    void huyDonDangCho_thanhCong_huyLanNua_tra422() throws Exception {
        HttpResponse<String> datDon = datMon(UUID.randomUUID(), donGianDon());
        String orderId = layTruong(datDon.body(), "id");

        HttpResponse<String> huyLanDau = client.send(HttpRequest.newBuilder(
                        uri("/api/v1/guest/orders/" + orderId + "/cancel"))
                        .header("Authorization", "Bearer " + token)
                        .POST(BodyPublishers.noBody())
                        .build(),
                BodyHandlers.ofString());
        assertThat(huyLanDau.statusCode()).isEqualTo(200);
        assertThat(huyLanDau.body()).contains("\"status\":\"CANCELLED\"");

        HttpResponse<String> huyLanHai = client.send(HttpRequest.newBuilder(
                        uri("/api/v1/guest/orders/" + orderId + "/cancel"))
                        .header("Authorization", "Bearer " + token)
                        .POST(BodyPublishers.noBody())
                        .build(),
                BodyHandlers.ofString());
        assertThat(huyLanHai.statusCode()).isEqualTo(422);
        assertThat(huyLanHai.body()).contains("\"code\":\"INVALID_TRANSITION\"");
    }

    @Test
    void goiNhanVien_thanhCong_goiLaiNgaySauDoBiChan() throws Exception {
        String body = "{\"reason\":\"REFILL_WATER\"}";
        HttpResponse<String> lanDau = client.send(HttpRequest.newBuilder(uri("/api/v1/guest/staff-calls"))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + token)
                        .POST(BodyPublishers.ofString(body))
                        .build(),
                BodyHandlers.ofString());
        assertThat(lanDau.statusCode()).isEqualTo(202);

        HttpResponse<String> lanHai = client.send(HttpRequest.newBuilder(uri("/api/v1/guest/staff-calls"))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + token)
                        .POST(BodyPublishers.ofString(body))
                        .build(),
                BodyHandlers.ofString());
        assertThat(lanHai.statusCode()).isEqualTo(429);
    }

    private String donGianDon() {
        return "{\"lines\":[{\"menuItemId\":\"%s\",\"variantId\":\"%s\",\"optionIds\":[\"%s\"],\"quantity\":1}]}"
                .formatted(itemId, variantId, optionChoiceId);
    }

    private HttpResponse<String> datMon(UUID idempotencyKey, String body) throws Exception {
        return client.send(HttpRequest.newBuilder(uri("/api/v1/guest/orders"))
                        .header("Content-Type", "application/json")
                        .header("Idempotency-Key", idempotencyKey.toString())
                        .header("Authorization", "Bearer " + token)
                        .POST(BodyPublishers.ofString(body))
                        .build(),
                BodyHandlers.ofString());
    }

    private static String layTruong(String json, String truong) {
        String canTim = "\"" + truong + "\":\"";
        int start = json.indexOf(canTim) + canTim.length();
        int end = json.indexOf('"', start);
        return json.substring(start, end);
    }

    private static String maNgauNhien() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase(Locale.ROOT);
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}
