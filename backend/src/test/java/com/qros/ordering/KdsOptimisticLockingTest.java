package com.qros.ordering;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import com.qros.generated.model.UpdateOrderLineStatusRequest;
import com.qros.generated.model.UpdateOrderLineStatusRequest.StatusEnum;
import com.qros.integration.QrosIntegrationTest;
import com.qros.ordering.controller.KdsController;
import com.qros.shared.error.ErrorCode;
import com.qros.shared.error.QrosException;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "qros.outbox.scheduler-enabled=false")
class KdsOptimisticLockingTest extends QrosIntegrationTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private KdsController controller;

    private UUID storeId;
    private UUID userId;
    private UUID lineId;
    private UUID orderId;

    @BeforeEach
    void prepare() {
        storeId = UUID.randomUUID();
        userId = UUID.randomUUID();
        UUID tableId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        orderId = UUID.randomUUID();
        lineId = UUID.randomUUID();

        jdbc.update("INSERT INTO store (id, code, name, timezone, opens_at, closes_at, active) VALUES (?,?,?,'UTC','00:00','23:59',true)",
                storeId, "KDS-" + storeId, "KDS test");
        jdbc.update("INSERT INTO restaurant_table (id, store_id, label, short_code, status) VALUES (?,?,?,'KDST01','OCCUPIED')",
                tableId, storeId, "KDS-01");
        jdbc.update("INSERT INTO table_session (id, store_id, table_id, status, opened_at, last_activity_at, expires_at) VALUES (?,?,?,'OPEN',now(),now(),now()+interval '90 minutes')",
                sessionId, storeId, tableId);
        jdbc.update("INSERT INTO category (id, store_id, name, display_order, active) VALUES (?,?,?,0,true)", categoryId, storeId, "Test");
        jdbc.update("INSERT INTO menu_item (id, store_id, category_id, name, station, allergens, attributes, published, manually_disabled, display_order) VALUES (?,?,?,'Espresso','COFFEE','{}','{}',true,false,0)",
                itemId, storeId, categoryId);
        jdbc.update("INSERT INTO menu_variant (id, menu_item_id, name, price_amount, display_order, active) VALUES (?,?,'Vừa',30000,0,true)", variantId, itemId);
        jdbc.update("INSERT INTO customer_order (id, store_id, table_id, session_id, short_code, status, subtotal_amount, total_amount, placed_at) VALUES (?,?,?,?,?,'CONFIRMED',30000,30000,now())",
                orderId, storeId, tableId, sessionId, "KDS-01");
        jdbc.update("INSERT INTO order_line (id, order_id, menu_item_id, menu_variant_id, item_name, variant_name, unit_price, quantity, line_total, station, status) VALUES (?,?,?,?,?,?,30000,1,30000,'COFFEE','CONFIRMED')",
                lineId, orderId, itemId, variantId, "Espresso", "Vừa");
        jdbc.update("INSERT INTO app_user (id, email, display_name, active) VALUES (?,?,?,true)", userId, userId + "@test.local", "Minh");
        jdbc.update("INSERT INTO user_role (id, user_id, role_id, store_id) SELECT ?, ?, id, ? FROM role WHERE code='BARISTA'",
                UUID.randomUUID(), userId, storeId);
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM outbox_event WHERE store_id = ?", storeId);
        jdbc.update("DELETE FROM order_status_log WHERE order_id = ?", orderId);
        jdbc.update("DELETE FROM user_role WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM app_user WHERE id = ?", userId);
        jdbc.update("DELETE FROM order_line WHERE id = ?", lineId);
        jdbc.update("DELETE FROM customer_order WHERE store_id = ?", storeId);
        jdbc.update("DELETE FROM table_session WHERE store_id = ?", storeId);
        jdbc.update("DELETE FROM menu_variant WHERE menu_item_id IN (SELECT id FROM menu_item WHERE store_id = ?)", storeId);
        jdbc.update("DELETE FROM menu_item WHERE store_id = ?", storeId);
        jdbc.update("DELETE FROM category WHERE store_id = ?", storeId);
        jdbc.update("DELETE FROM restaurant_table WHERE store_id = ?", storeId);
        jdbc.update("DELETE FROM store WHERE id = ?", storeId);
    }

    @Test
    void ec06_namMuoiNguoiCungBam_chiMotThanhCong_bonMuoiChinXungDot() throws Exception {
        int workers = 50;
        CyclicBarrier barrier = new CyclicBarrier(workers);
        List<Boolean> results = new ArrayList<>();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = java.util.stream.IntStream.range(0, workers).mapToObj(index -> executor.submit(() -> {
                authenticate();
                barrier.await();
                try {
                    controller.updateStatus(lineId, "0", storeId, UUID.randomUUID(),
                            new UpdateOrderLineStatusRequest(StatusEnum.PREPARING));
                    return true;
                } catch (QrosException exception) {
                    assertThat(exception.errorCode()).isEqualTo(ErrorCode.VERSION_CONFLICT);
                    assertThat(exception.extensions()).containsKey("current");
                    return false;
                } finally {
                    SecurityContextHolder.clearContext();
                }
            })).toList();
            for (var future : futures) results.add(future.get());
        }

        assertThat(results).containsExactlyInAnyOrderElementsOf(
                java.util.stream.Stream.concat(java.util.stream.Stream.of(true),
                        java.util.stream.Stream.generate(() -> false).limit(49)).toList());
        assertThat(jdbc.queryForObject("SELECT status FROM order_line WHERE id = ?", String.class, lineId))
                .isEqualTo("PREPARING");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM order_status_log WHERE order_line_id = ?", Long.class, lineId))
                .isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT status FROM customer_order WHERE id = ?", String.class, orderId))
                .isEqualTo("PREPARING");
    }

    @Test
    void frCus10_dongBoTrangThaiTong_denKhiDonDaPhucVu() {
        authenticate();
        UUID deviceId = UUID.randomUUID();
        controller.updateStatus(lineId, "0", storeId, deviceId,
                new UpdateOrderLineStatusRequest(StatusEnum.PREPARING));
        controller.updateStatus(lineId, "1", storeId, deviceId,
                new UpdateOrderLineStatusRequest(StatusEnum.READY));
        controller.updateStatus(lineId, "2", storeId, deviceId,
                new UpdateOrderLineStatusRequest(StatusEnum.SERVED));

        assertThat(jdbc.queryForObject("SELECT status FROM customer_order WHERE id = ?", String.class, orderId))
                .isEqualTo("SERVED");
        assertThat(jdbc.queryForList("SELECT to_status FROM order_status_log WHERE order_id = ? "
                + "AND order_line_id IS NULL ORDER BY id", String.class, orderId))
                .containsExactly("PREPARING", "READY", "SERVED");
    }

    private void authenticate() {
        Instant now = Instant.now();
        Jwt jwt = new Jwt("test", now, now.plusSeconds(60), Map.of("alg", "none"),
                Map.of("sub", userId.toString(), "scope", "staff"));
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }
}
