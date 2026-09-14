package com.qros.catalog.domain;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/** BOM: nguyên liệu một biến thể món tiêu hao — chỉ cần cặp id để tính "còn/hết" ở tầng đọc này. */
@Entity
@Table(name = "recipe_component")
@IdClass(RecipeComponentId.class)
public class RecipeComponent {

    @Id
    @Column(name = "menu_variant_id", nullable = false)
    private UUID menuVariantId;

    @Id
    @Column(name = "ingredient_id", nullable = false)
    private UUID ingredientId;

    protected RecipeComponent() {
        // JPA
    }

    public UUID getMenuVariantId() {
        return menuVariantId;
    }

    public UUID getIngredientId() {
        return ingredientId;
    }
}
