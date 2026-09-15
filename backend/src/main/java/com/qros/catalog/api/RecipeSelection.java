package com.qros.catalog.api;

import java.util.List;
import java.util.UUID;

/** Biến thể và tuỳ chọn đã chọn của một dòng; {@code key} do bên gọi tự gắn để ghép kết quả. */
public record RecipeSelection(UUID key, UUID variantId, List<UUID> optionChoiceIds) {
}
