package com.qros.catalog.domain;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "option_choice")
public class OptionChoice {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "option_group_id", nullable = false)
    private UUID optionGroupId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "surcharge_amount", nullable = false)
    private long surchargeAmount;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "active", nullable = false)
    private boolean active;

    protected OptionChoice() {
        // JPA
    }

    public UUID getId() {
        return id;
    }

    public UUID getOptionGroupId() {
        return optionGroupId;
    }

    public String getName() {
        return name;
    }

    public long getSurchargeAmount() {
        return surchargeAmount;
    }

    public boolean isActive() {
        return active;
    }
}
