package com.qros.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import com.qros.catalog.service.MenuService;
import com.qros.integration.QrosIntegrationTest;
import com.qros.inventory.controller.InventoryController;
import com.qros.inventory.service.InventoryService;
import com.qros.ordering.service.KdsService;
import com.qros.shared.error.ErrorCode;
import com.qros.shared.error.QrosException;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "qros.outbox.scheduler-enabled=false")
class InventorySoldOutTest extends QrosIntegrationTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private InventoryController inventoryController;
    @Autowired private InventoryService inventoryService;
    @Autowired private MenuService menuService;
    @Autowired private KdsService kdsService;

    private UUID storeId;
    private UUID userId;
    private UUID ingredientId;
    private UUID itemId;
    private UUID orderId;

    @BeforeEach
    void prepare() {
        storeId = UUID.randomUUID();
        userId = UUID.randomUUID();
        ingredientId = UUID.randomUUID();
        itemId = UUID.randomUUID();
        orderId = UUID.randomUUID();
        UUID tableId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        UUID lineId = UUID.randomUUID();

        jdbc.update("INSERT INTO store (id, code, name, timezone, opens_at, closes_at, active) VALUES (?,?,?,'UTC','00:00','23:59',true)",
                storeId, "INV-" + storeId, "Inventory test");
        jdbc.update("INSERT INTO restaurant_table (id, store_id, label, short_code, status) VALUES (?,?,?,'INVT01','OCCUPIED')",
                tableId, storeId, "INV-01");
        jdbc.update("INSERT INTO table_session (id, store_id, table_id, status, opened_at, last_activity_at, expires_at) VALUES (?,?,?,'OPEN',now(),now(),now()+interval '90 minutes')",
                sessionId, storeId, tableId);
        jdbc.update("INSERT INTO category (id, store_id, name, display_order, active) VALUES (?,?,?,0,true)",
                categoryId, storeId, "Trà sữa");
        jdbc.update("INSERT INTO menu_item (id, store_id, category_id, name, station, allergens, attributes, published, manually_disabled, display_order) VALUES (?,?,?,'Trà sữa trân châu','TEA','{}','{}',true,false,0)",
                itemId, storeId, categoryId);
        jdbc.update("INSERT INTO menu_variant (id, menu_item_id, name, price_amount, display_order, active) VALUES (?,?,'Vừa',45000,0,true)",
                variantId, itemId);
        jdbc.update("INSERT INTO ingredient (id, store_id, name, unit, sold_out) VALUES (?,?,?,'kg',false)",
                ingredientId, storeId, "Trân châu đen");
        jdbc.update("INSERT INTO recipe_component (menu_variant_id, ingredient_id, quantity) VALUES (?,?,0.05)",
                variantId, ingredientId);
        jdbc.update("INSERT INTO customer_order (id, store_id, table_id, session_id, short_code, status, subtotal_amount, total_amount, placed_at) VALUES (?,?,?,?,?,'CONFIRMED',45000,45000,now())",
                orderId, storeId, tableId, sessionId, "INV-01");
        jdbc.update("INSERT INTO order_line (id, order_id, menu_item_id, menu_variant_id, item_name, variant_name, unit_price, quantity, line_total, station, status) VALUES (?,?,?,?,?,?,45000,1,45000,'TEA','CONFIRMED')",
                lineId, orderId, itemId, variantId, "Trà sữa trân châu", "Vừa");
        jdbc.update("INSERT INTO app_user (id, email, display_name, active) VALUES (?,?,?,true)",
                userId, userId + "@test.local", "Minh");
        jdbc.update("INSERT INTO user_role (id, user_id, role_id, store_id) SELECT ?, ?, id, ? FROM role WHERE code='BARISTA'",
                UUID.randomUUID(), userId, storeId);
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
        jdbc.update("DELETE FROM outbox_event WHERE store_id = ?", storeId);
        jdbc.update("DELETE FROM user_role WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM app_user WHERE id = ?", userId);
        jdbc.update("DELETE FROM order_line WHERE order_id = ?", orderId);
        jdbc.update("DELETE FROM customer_order WHERE id = ?", orderId);
        jdbc.update("DELETE FROM table_session WHERE store_id = ?", storeId);
        jdbc.update("DELETE FROM recipe_component WHERE ingredient_id = ?", ingredientId);
        jdbc.update("DELETE FROM menu_variant WHERE menu_item_id = ?", itemId);
        jdbc.update("DELETE FROM menu_item WHERE id = ?", itemId);
        jdbc.update("DELETE FROM category WHERE store_id = ?", storeId);
        jdbc.update("DELETE FROM ingredient WHERE id = ?", ingredientId);
        jdbc.update("DELETE FROM restaurant_table WHERE store_id = ?", storeId);
        jdbc.update("DELETE FROM store WHERE id = ?", storeId);
    }

    @Test
    void frBar05_baoHet_lanToaMenuKdsVaDonDangCho_duoiHaiGiay() {
        var queueBefore = kdsService.getQueue(userId, storeId, "ALL");
        assertThat(queueBefore.getTickets().getFirst().getLines().getFirst().getIngredients())
                .singleElement().satisfies(ingredient -> {
                    assertThat(ingredient.getId()).isEqualTo(ingredientId);
                    assertThat(ingredient.getSoldOut()).isFalse();
        });

        Instant started = Instant.now();
        authenticate();
        var result = inventoryController.markSoldOut(ingredientId, storeId).getBody();
        var menu = menuService.buildMenu(storeId);

        assertThat(result).isNotNull();
        assertThat(Duration.between(started, Instant.now())).isLessThan(Duration.ofSeconds(2));
        assertThat(result.getAffectedItemIds()).containsExactly(itemId);
        assertThat(result.getAffectedOpenOrderIds()).containsExactly(orderId);
        assertThat(menu.categories().getFirst().items().getFirst().available()).isFalse();
        assertThat(jdbc.queryForObject("SELECT sold_out FROM ingredient WHERE id = ?", Boolean.class, ingredientId))
                .isTrue();
        assertThat(jdbc.queryForList("SELECT type FROM outbox_event WHERE store_id = ? ORDER BY id", String.class,
                storeId)).containsExactly("IngredientSoldOut", "ItemUnavailable");
        assertThat(jdbc.queryForObject("SELECT payload::text FROM outbox_event WHERE store_id = ? AND type='IngredientSoldOut'",
                String.class, storeId)).contains(itemId.toString(), orderId.toString(), "Trân châu đen");
        assertThat(jdbc.queryForObject("SELECT payload::text FROM outbox_event WHERE store_id = ? AND type='ItemUnavailable'",
                String.class, storeId)).contains(orderId.toString(), "respondBy", "45000");

        inventoryService.markSoldOut(userId, storeId, ingredientId);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_event WHERE store_id = ?", Long.class, storeId))
                .isEqualTo(2L);
    }

    @Test
    void batBienSo7_khongCoQuyenChiNhanh_tra404() {
        assertThatThrownBy(() -> inventoryService.markSoldOut(userId, UUID.randomUUID(), ingredientId))
                .isInstanceOfSatisfying(QrosException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    private void authenticate() {
        Instant now = Instant.now();
        Jwt jwt = new Jwt("test", now, now.plusSeconds(60), Map.of("alg", "none"),
                Map.of("sub", userId.toString(), "scope", "staff"));
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }
}
