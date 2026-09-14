package com.qros.catalog.domain;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/** Topping cũng tiêu hao nguyên liệu — chú thích gốc ở {@code V1__baseline.sql}. */
@Entity
@Table(name = "option_recipe_component")
@IdClass(OptionRecipeComponentId.class)
public class OptionRecipeComponent {

    @Id
    @Column(name = "option_choice_id", nullable = false)
    private UUID optionChoiceId;

    @Id
    @Column(name = "ingredient_id", nullable = false)
    private UUID ingredientId;

    protected OptionRecipeComponent() {
        // JPA
    }

    public UUID getOptionChoiceId() {
        return optionChoiceId;
    }

    public UUID getIngredientId() {
        return ingredientId;
    }
}
