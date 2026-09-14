package com.qros.catalog.domain;

import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Một món trong thực đơn. Tên lớp tránh trùng {@code com.qros.generated.model.MenuItem} (DTO hợp
 * đồng) — cùng lý do {@code TableSessionEntity} ở module {@code venue}.
 */
@Entity
@Table(name = "menu_item")
public class MenuItemEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "image_url")
    private String imageUrl;

    // Sai ở cột này là rủi ro sức khoẻ, không phải lỗi hiển thị — chú thích gốc ở V1__baseline.sql.
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "allergens", nullable = false)
    private List<String> allergens;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "attributes", nullable = false)
    private List<String> attributes;

    // COFFEE/TEA/FOOD — trạm pha chế sao chép sang order_line.station lúc đặt món (ordering, BL-M1-03).
    @Column(name = "station", nullable = false)
    private String station;

    @Column(name = "published", nullable = false)
    private boolean published;

    @Column(name = "manually_disabled", nullable = false)
    private boolean manuallyDisabled;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Version
    @Column(name = "version", nullable = false)
    private int version;

    protected MenuItemEntity() {
        // JPA
    }

    public UUID getId() {
        return id;
    }

    public UUID getStoreId() {
        return storeId;
    }

    public UUID getCategoryId() {
        return categoryId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public List<String> getAllergens() {
        return allergens;
    }

    public List<String> getAttributes() {
        return attributes;
    }

    public String getStation() {
        return station;
    }

    public boolean isPublished() {
        return published;
    }

    /** {@code FR-CUS-03}: món hết hàng hiển thị mờ — nhưng đó là "còn/hết", không phải "hiển thị". */
    public boolean isVisible() {
        return published && !manuallyDisabled;
    }
}
