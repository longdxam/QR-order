package com.qros.catalog.api;

import java.util.List;
import java.util.UUID;

/** Ảnh hưởng của một nguyên liệu lên cấu trúc catalog. */
public record IngredientImpact(UUID ingredientId, String ingredientName, List<UUID> affectedItemIds,
        List<UUID> affectedVariantIds, List<UUID> affectedOptionChoiceIds) {
}
