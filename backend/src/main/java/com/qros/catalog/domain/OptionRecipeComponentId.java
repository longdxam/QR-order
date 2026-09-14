package com.qros.catalog.domain;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public final class OptionRecipeComponentId implements Serializable {

    private UUID optionChoiceId;
    private UUID ingredientId;

    public OptionRecipeComponentId() {
        // JPA
    }

    public OptionRecipeComponentId(UUID optionChoiceId, UUID ingredientId) {
        this.optionChoiceId = optionChoiceId;
        this.ingredientId = ingredientId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof OptionRecipeComponentId that)) {
            return false;
        }
        return Objects.equals(optionChoiceId, that.optionChoiceId) && Objects.equals(ingredientId, that.ingredientId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(optionChoiceId, ingredientId);
    }
}
