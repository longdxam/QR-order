package com.qros.catalog.domain;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public final class RecipeComponentId implements Serializable {

    private UUID menuVariantId;
    private UUID ingredientId;

    public RecipeComponentId() {
        // JPA
    }

    public RecipeComponentId(UUID menuVariantId, UUID ingredientId) {
        this.menuVariantId = menuVariantId;
        this.ingredientId = ingredientId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof RecipeComponentId that)) {
            return false;
        }
        return Objects.equals(menuVariantId, that.menuVariantId) && Objects.equals(ingredientId, that.ingredientId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(menuVariantId, ingredientId);
    }
}
