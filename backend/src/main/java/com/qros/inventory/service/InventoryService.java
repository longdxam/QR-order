package com.qros.inventory.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.qros.catalog.api.CatalogFacade;
import com.qros.catalog.api.IngredientImpact;
import com.qros.generated.model.IngredientSoldOutResult;
import com.qros.identity.api.StaffAccessFacade;
import com.qros.inventory.domain.InventoryIngredient;
import com.qros.inventory.repository.InventoryIngredientRepository;
import com.qros.ordering.api.AffectedOpenOrder;
import com.qros.ordering.api.OpenOrderImpactFacade;
import com.qros.shared.error.ErrorCode;
import com.qros.shared.error.QrosException;
import com.qros.shared.event.DomainEvent;
import com.qros.shared.event.OutboxWriter;

@Service
@ConditionalOnProperty(name = "spring.datasource.url")
public class InventoryService {

    private static final String ADJUST_PERMISSION = "inventory:adjust";
    private final InventoryIngredientRepository ingredientRepository;
    private final StaffAccessFacade staffAccessFacade;
    private final CatalogFacade catalogFacade;
    private final OpenOrderImpactFacade orderImpactFacade;
    private final OutboxWriter outboxWriter;
    private final Clock clock;

    public InventoryService(InventoryIngredientRepository ingredientRepository,
            StaffAccessFacade staffAccessFacade, CatalogFacade catalogFacade,
            OpenOrderImpactFacade orderImpactFacade, OutboxWriter outboxWriter, Clock clock) {
        this.ingredientRepository = ingredientRepository;
        this.staffAccessFacade = staffAccessFacade;
        this.catalogFacade = catalogFacade;
        this.orderImpactFacade = orderImpactFacade;
        this.outboxWriter = outboxWriter;
        this.clock = clock;
    }

    @Transactional
    public IngredientSoldOutResult markSoldOut(UUID actorId, UUID storeId, UUID ingredientId) {
        staffAccessFacade.findAuthorized(actorId, storeId, ADJUST_PERMISSION)
                .orElseThrow(() -> new QrosException(ErrorCode.NOT_FOUND));
        InventoryIngredient ingredient = ingredientRepository.findByIdAndStoreId(ingredientId, storeId)
                .orElseThrow(() -> new QrosException(ErrorCode.NOT_FOUND));
        IngredientImpact impact = catalogFacade.ingredientImpact(storeId, ingredientId);
        List<AffectedOpenOrder> affectedOrders = orderImpactFacade.findAffected(storeId,
                impact.affectedVariantIds(), impact.affectedOptionChoiceIds());

        if (ingredient.markSoldOut()) {
            ingredientRepository.saveAndFlush(ingredient);
            Instant now = Instant.now(clock);
            appendStoreEvent(storeId, ingredient, impact, affectedOrders, now);
            appendGuestEvents(storeId, affectedOrders, now);
        }

        return new IngredientSoldOutResult(ingredientId, ingredient.getName(), impact.affectedItemIds(),
                affectedOrders.stream().map(AffectedOpenOrder::orderId).toList());
    }

    private void appendStoreEvent(UUID storeId, InventoryIngredient ingredient, IngredientImpact impact,
            List<AffectedOpenOrder> affectedOrders, Instant now) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ingredientId", ingredient.getId().toString());
        payload.put("ingredientName", ingredient.getName());
        payload.put("affectedItemIds", impact.affectedItemIds().stream().map(UUID::toString).toList());
        payload.put("affectedOpenOrderIds", affectedOrders.stream()
                .map(order -> order.orderId().toString()).toList());
        outboxWriter.append(new InventoryEvent(DomainEvent.newEventId(), ingredient.getId(), storeId,
                null, now, "IngredientSoldOut", payload));
    }

    private void appendGuestEvents(UUID storeId, List<AffectedOpenOrder> orders, Instant now) {
        Instant respondBy = now.plus(Duration.ofMinutes(3));
        orders.forEach(order -> order.lines().forEach(line -> {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("orderId", order.orderId().toString());
            payload.put("lineId", line.lineId().toString());
            payload.put("itemName", line.itemName());
            payload.put("refundAmount", Map.of("amount", line.refundAmount(), "currency", "VND"));
            payload.put("alternatives", List.of());
            payload.put("respondBy", respondBy.toString());
            outboxWriter.append(new InventoryEvent(DomainEvent.newEventId(), line.lineId(), storeId,
                    order.sessionId(), now, "ItemUnavailable", payload));
        }));
    }

    private record InventoryEvent(UUID eventId, UUID aggregateId, UUID storeId, UUID sessionId,
            Instant occurredAt, String type, Object payload) implements DomainEvent {
        @Override public String aggregateType() { return "Ingredient"; }
    }
}
