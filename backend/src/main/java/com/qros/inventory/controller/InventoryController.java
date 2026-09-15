package com.qros.inventory.controller;

import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.qros.generated.model.IngredientSoldOutResult;
import com.qros.inventory.service.InventoryService;
import com.qros.shared.error.ErrorCode;
import com.qros.shared.error.QrosException;

@RestController
@ConditionalOnProperty(name = "spring.datasource.url")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @PostMapping("/api/v1/staff/ingredients/{ingredientId}/sold-out")
    public ResponseEntity<IngredientSoldOutResult> markSoldOut(@PathVariable UUID ingredientId,
            @RequestHeader("X-Store-Id") UUID storeId) {
        return ResponseEntity.ok(inventoryService.markSoldOut(actorId(), storeId, ingredientId));
    }

    private static UUID actorId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof Jwt jwt)) throw new QrosException(ErrorCode.UNAUTHENTICATED);
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException exception) {
            throw new QrosException(ErrorCode.UNAUTHENTICATED);
        }
    }
}
