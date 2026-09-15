package com.qros.inventory.domain;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** Aggregate kho tối thiểu của BL-M1-05; định lượng và nhập kho thuộc M2. */
@Entity
@Table(name = "ingredient")
public class InventoryIngredient {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "sold_out", nullable = false)
    private boolean soldOut;

    @Version
    @Column(name = "version", nullable = false)
    private int version;

    protected InventoryIngredient() {
        // JPA
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    /** Trả false khi thao tác đã được áp dụng trước đó, để không phát trùng hiệu ứng nghiệp vụ. */
    public boolean markSoldOut() {
        if (soldOut) return false;
        soldOut = true;
        return true;
    }
}
