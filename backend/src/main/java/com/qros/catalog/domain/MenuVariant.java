package com.qros.catalog.domain;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "menu_variant")
public class MenuVariant {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "menu_item_id", nullable = false)
    private UUID menuItemId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "price_amount", nullable = false)
    private long priceAmount;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "active", nullable = false)
    private boolean active;

    protected MenuVariant() {
        // JPA
    }

    /** Gói hẹp trong package — lối vào hợp lệ duy nhất từ test là {@code CatalogFixtures}. */
    MenuVariant(UUID id, UUID menuItemId, String name, long priceAmount, boolean active) {
        this.id = id;
        this.menuItemId = menuItemId;
        this.name = name;
        this.priceAmount = priceAmount;
        this.active = active;
    }

    public UUID getId() {
        return id;
    }

    public UUID getMenuItemId() {
        return menuItemId;
    }

    public String getName() {
        return name;
    }

    public long getPriceAmount() {
        return priceAmount;
    }

    public boolean isActive() {
        return active;
    }
}
