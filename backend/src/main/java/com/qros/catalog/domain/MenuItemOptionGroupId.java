package com.qros.catalog.domain;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Khoá phức hợp của {@link MenuItemOptionGroup} — khớp {@code PRIMARY KEY (menu_item_id, option_group_id)}. */
public final class MenuItemOptionGroupId implements Serializable {

    private UUID menuItemId;
    private UUID optionGroupId;

    public MenuItemOptionGroupId() {
        // JPA
    }

    public MenuItemOptionGroupId(UUID menuItemId, UUID optionGroupId) {
        this.menuItemId = menuItemId;
        this.optionGroupId = optionGroupId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof MenuItemOptionGroupId that)) {
            return false;
        }
        return Objects.equals(menuItemId, that.menuItemId) && Objects.equals(optionGroupId, that.optionGroupId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(menuItemId, optionGroupId);
    }
}
