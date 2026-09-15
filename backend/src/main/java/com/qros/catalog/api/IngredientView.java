package com.qros.catalog.api;

import java.util.UUID;

/** Hình chiếu nguyên liệu tối thiểu được phép đi qua ranh giới module. */
public record IngredientView(UUID id, String name, boolean soldOut) {
}
