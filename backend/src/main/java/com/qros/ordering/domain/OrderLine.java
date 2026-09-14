package com.qros.ordering.domain;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import com.qros.shared.id.UuidV7;

@Entity
@Table(name = "order_line")
public class OrderLine {

    public static final String STATUS_PENDING = "PENDING";

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "menu_item_id", nullable = false)
    private UUID menuItemId;

    @Column(name = "menu_variant_id", nullable = false)
    private UUID menuVariantId;

    @Column(name = "item_name", nullable = false)
    private String itemName;

    @Column(name = "variant_name", nullable = false)
    private String variantName;

    @Column(name = "unit_price", nullable = false)
    private long unitPrice;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "line_total", nullable = false)
    private long lineTotal;

    @Column(name = "note")
    private String note;

    @Column(name = "added_by")
    private String addedBy;

    @Column(name = "station", nullable = false)
    private String station;

    @Column(name = "status", nullable = false)
    private String status;

    @Version
    @Column(name = "version", nullable = false)
    private int version;

    protected OrderLine() {
        // JPA
    }

    public static OrderLine moi(UUID orderId, UUID menuItemId, UUID menuVariantId, String itemName,
            String variantName, long unitPrice, int quantity, String note, String addedBy, String station) {
        OrderLine line = new OrderLine();
        line.id = UuidV7.generate();
        line.orderId = orderId;
        line.menuItemId = menuItemId;
        line.menuVariantId = menuVariantId;
        line.itemName = itemName;
        line.variantName = variantName;
        line.unitPrice = unitPrice;
        line.quantity = quantity;
        line.lineTotal = Math.multiplyExact(unitPrice, quantity);
        line.note = note;
        line.addedBy = addedBy;
        line.station = station;
        line.status = STATUS_PENDING;
        return line;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public UUID getMenuItemId() {
        return menuItemId;
    }

    public String getItemName() {
        return itemName;
    }

    public String getVariantName() {
        return variantName;
    }

    public int getQuantity() {
        return quantity;
    }

    public long getUnitPrice() {
        return unitPrice;
    }

    public long getLineTotal() {
        return lineTotal;
    }

    public String getNote() {
        return note;
    }

    public String getAddedBy() {
        return addedBy;
    }

    public String getStation() {
        return station;
    }

    public String getStatus() {
        return status;
    }

    public int getVersion() {
        return version;
    }
}
