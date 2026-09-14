package com.qros.catalog.domain;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

@Entity
@Table(name = "menu_item_option_group")
@IdClass(MenuItemOptionGroupId.class)
public class MenuItemOptionGroup {

    @Id
    @Column(name = "menu_item_id", nullable = false)
    private UUID menuItemId;

    @Id
    @Column(name = "option_group_id", nullable = false)
    private UUID optionGroupId;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    protected MenuItemOptionGroup() {
        // JPA
    }

    public UUID getMenuItemId() {
        return menuItemId;
    }

    public UUID getOptionGroupId() {
        return optionGroupId;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }
}
