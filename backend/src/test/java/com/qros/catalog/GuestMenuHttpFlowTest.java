package com.qros.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
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
 * {@code FR-CUS-03}, {@code NFR-PERF-01}: thực đơn đọc qua HTTP thật — không gọi thẳng
 * {@code MenuService}, để chuỗi bảo mật vùng khách (bearer token, ownership) tham gia vào phép thử.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GuestMenuHttpFlowTest extends QrosIntegrationTest {

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtIssuer guestJwtIssuer;

    private final HttpClient client = HttpClient.newHttpClient();
    private UUID storeId;
    private UUID categoryId;
    private UUID itemId;
    private UUID variantId;
    private UUID ingredientId;
    private final List<UUID> storeIdsDaTao = new ArrayList<>();

    @BeforeEach
    void chuanBi() {
        storeId = taoChiNhanh("Quán kiểm thử thực đơn");

        categoryId = UuidV7.generate();
        jdbcTemplate.update("INSERT INTO category (id, store_id, name, display_order, active) VALUES (?,?,?,0,true)",
                categoryId, storeId, "Trà sữa");

        itemId = UuidV7.generate();
        jdbcTemplate.update("""
                INSERT INTO menu_item
                    (id, store_id, category_id, name, description, allergens, attributes, published, manually_disabled, display_order)
                VALUES (?, ?, ?, 'Trà sữa trân châu', 'Vị truyền thống', '{MILK}', '{COLD}', true, false, 0)""",
                itemId, storeId, categoryId);

        variantId = UuidV7.generate();
        jdbcTemplate.update("""
                INSERT INTO menu_variant (id, menu_item_id, name, price_amount, display_order, active)
                VALUES (?, ?, 'Size M', 45000, 0, true)""",
                variantId, itemId);

        ingredientId = UuidV7.generate();
        jdbcTemplate.update("""
                INSERT INTO ingredient (id, store_id, name, unit, sold_out)
                VALUES (?, ?, 'Trân châu đen', 'kg', false)""",
                ingredientId, storeId);
        jdbcTemplate.update("""
                INSERT INTO recipe_component (menu_variant_id, ingredient_id, quantity)
                VALUES (?, ?, 0.05)""",
                variantId, ingredientId);
    }

    @Test
    void layThucDon_traDungMonVaGia() throws Exception {
        HttpResponse<String> phanHoi = layThucDon(null, tokenChoStore(storeId));

        assertThat(phanHoi.statusCode()).isEqualTo(200);
        assertThat(phanHoi.body()).contains("Trà sữa trân châu")
                .contains("\"available\":true")
                .contains("45000");
        assertThat(phanHoi.headers().firstValue("ETag")).isPresent();
        assertThat(phanHoi.headers().firstValue("Cache-Control")).hasValueSatisfying(
                value -> assertThat(value).contains("private", "no-cache", "must-revalidate"));
    }

    @Test
    void guiLaiEtagCu_traKhongDoi304() throws Exception {
        String token = tokenChoStore(storeId);
        HttpResponse<String> lanDau = layThucDon(null, token);
        String etag = lanDau.headers().firstValue("ETag").orElseThrow();

        HttpResponse<String> lanHai = layThucDon(etag, token);

        assertThat(lanHai.statusCode()).isEqualTo(304);
        assertThat(lanHai.body()).isEmpty();
    }

    @Test
    void nguyenLieuHetHang_monHienThiHetHangVaEtagDoi() throws Exception {
        String token = tokenChoStore(storeId);
        String etagTruoc = layThucDon(null, token).headers().firstValue("ETag").orElseThrow();

        jdbcTemplate.update("UPDATE ingredient SET sold_out = true WHERE id = ?", ingredientId);

        HttpResponse<String> sauKhiHet = layThucDon(null, token);
        assertThat(sauKhiHet.body()).contains("\"available\":false");
        String etagSau = sauKhiHet.headers().firstValue("ETag").orElseThrow();
        assertThat(etagSau).isNotEqualTo(etagTruoc);
    }

    @Test
    void chiTietMon_traDungMon() throws Exception {
        HttpResponse<String> phanHoi = client.send(HttpRequest.newBuilder(uri("/api/v1/guest/menu/items/" + itemId))
                        .header("Authorization", "Bearer " + tokenChoStore(storeId))
                        .GET().build(),
                BodyHandlers.ofString());

        assertThat(phanHoi.statusCode()).isEqualTo(200);
        assertThat(phanHoi.body()).contains("Trà sữa trân châu").contains("MILK");
    }

    @Test
    void chiTietMon_khongTonTai_tra404() throws Exception {
        HttpResponse<String> phanHoi = client.send(HttpRequest.newBuilder(
                        uri("/api/v1/guest/menu/items/" + UuidV7.generate()))
                        .header("Authorization", "Bearer " + tokenChoStore(storeId))
                        .GET().build(),
                BodyHandlers.ofString());

        assertThat(phanHoi.statusCode()).isEqualTo(404);
    }

    /** Bất biến số 7: token của chi nhánh khác không xem được món của chi nhánh này — 404, không phải 403. */
    @Test
    void chiTietMon_tokenChiNhanhKhac_tra404() throws Exception {
        UUID storeKhac = taoChiNhanh("Chi nhánh khác");

        HttpResponse<String> phanHoi = client.send(HttpRequest.newBuilder(uri("/api/v1/guest/menu/items/" + itemId))
                        .header("Authorization", "Bearer " + tokenChoStore(storeKhac))
                        .GET().build(),
                BodyHandlers.ofString());

        assertThat(phanHoi.statusCode()).isEqualTo(404);
    }

    @Test
    void khongCoToken_tra401() throws Exception {
        HttpResponse<String> phanHoi = client.send(HttpRequest.newBuilder(uri("/api/v1/guest/menu"))
                        .GET().build(),
                BodyHandlers.ofString());

        assertThat(phanHoi.statusCode()).isEqualTo(401);
    }

    private HttpResponse<String> layThucDon(String ifNoneMatch, String token) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri("/api/v1/guest/menu"))
                .header("Authorization", "Bearer " + token)
                .GET();
        if (ifNoneMatch != null) {
            request.header("If-None-Match", ifNoneMatch);
        }
        return client.send(request.build(), BodyHandlers.ofString());
    }

    private UUID taoChiNhanh(String ten) {
        UUID id = UuidV7.generate();
        jdbcTemplate.update("""
                INSERT INTO store (id, code, name, timezone, opens_at, closes_at, active)
                VALUES (?, ?, ?, 'UTC', '00:00:00', '23:59:59', true)""",
                id, "MENU-" + id, ten);
        storeIdsDaTao.add(id);
        return id;
    }

    /**
     * ⚠ Container CSDL dùng chung cho cả lượt chạy test ({@code QrosIntegrationTest}, mẫu
     * singleton) — không dọn thì {@code category}/{@code menu_item}/{@code ingredient} còn sót lại
     * làm vỡ FK của những test khác chạy sau, ví dụ {@code WorkShiftAutoCloseTest} xoá thẳng
     * {@code store} trong @BeforeEach của chính nó.
     */
    @AfterEach
    void donDep() {
        for (UUID id : storeIdsDaTao) {
            jdbcTemplate.update("""
                    DELETE FROM recipe_component WHERE menu_variant_id IN
                        (SELECT id FROM menu_variant WHERE menu_item_id IN
                            (SELECT id FROM menu_item WHERE store_id = ?))""", id);
            jdbcTemplate.update("""
                    DELETE FROM menu_variant WHERE menu_item_id IN
                        (SELECT id FROM menu_item WHERE store_id = ?)""", id);
            jdbcTemplate.update("DELETE FROM menu_item WHERE store_id = ?", id);
            jdbcTemplate.update("DELETE FROM category WHERE store_id = ?", id);
            jdbcTemplate.update("DELETE FROM ingredient WHERE store_id = ?", id);
            jdbcTemplate.update("DELETE FROM store WHERE id = ?", id);
        }
    }

    private String tokenChoStore(UUID storeId) {
        return guestJwtIssuer.issue(UuidV7.generate().toString(), Duration.ofMinutes(90), Map.of(
                "scope", "table_session",
                "sid", storeId.toString(),
                "tid", UuidV7.generate().toString(),
                "tv", 0));
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}
