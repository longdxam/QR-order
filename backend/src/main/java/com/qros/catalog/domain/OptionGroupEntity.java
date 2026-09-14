package com.qros.catalog.domain;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Tên lớp tránh trùng {@code com.qros.generated.model.OptionGroup} (DTO hợp đồng). */
@Entity
@Table(name = "option_group")
public class OptionGroupEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "selection", nullable = false)
    private String selection;

    @Column(name = "is_required", nullable = false)
    private boolean required;

    @Column(name = "min_select", nullable = false)
    private int minSelect;

    @Column(name = "max_select", nullable = false)
    private int maxSelect;

    protected OptionGroupEntity() {
        // JPA
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getSelection() {
        return selection;
    }

    public boolean isRequired() {
        return required;
    }

    public int getMinSelect() {
        return minSelect;
    }

    public int getMaxSelect() {
        return maxSelect;
    }
}
