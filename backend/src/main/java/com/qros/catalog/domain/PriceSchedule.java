package com.qros.catalog.domain;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * {@code FR-MGT-04}: đổi giá có hiệu lực tương lai, không sửa đè lịch sử — chú thích gốc ở
 * {@code V1__baseline.sql}. Chưa có endpoint quản trị nào ghi bảng này (M3); module {@code catalog}
 * ở {@code BL-M1-02} chỉ ĐỌC để tính giá hiệu lực tại một thời điểm, xem {@code EffectivePricing}.
 */
@Entity
@Table(name = "price_schedule")
public class PriceSchedule {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "menu_variant_id", nullable = false)
    private UUID menuVariantId;

    @Column(name = "price_amount", nullable = false)
    private long priceAmount;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "days_of_week", nullable = false)
    private List<Integer> daysOfWeek;

    @Column(name = "starts_at")
    private LocalTime startsAt;

    @Column(name = "ends_at")
    private LocalTime endsAt;

    @Column(name = "effective_from", nullable = false)
    private Instant effectiveFrom;

    @Column(name = "effective_to")
    private Instant effectiveTo;

    protected PriceSchedule() {
        // JPA
    }

    /** Gói hẹp trong package — lối vào hợp lệ duy nhất từ test là {@code CatalogFixtures}. */
    PriceSchedule(UUID id, UUID menuVariantId, long priceAmount, List<Integer> daysOfWeek,
            LocalTime startsAt, LocalTime endsAt, Instant effectiveFrom, Instant effectiveTo) {
        this.id = id;
        this.menuVariantId = menuVariantId;
        this.priceAmount = priceAmount;
        this.daysOfWeek = daysOfWeek;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
    }

    public UUID getMenuVariantId() {
        return menuVariantId;
    }

    public long getPriceAmount() {
        return priceAmount;
    }

    public List<Integer> getDaysOfWeek() {
        return daysOfWeek;
    }

    public LocalTime getStartsAt() {
        return startsAt;
    }

    public LocalTime getEndsAt() {
        return endsAt;
    }

    public Instant getEffectiveFrom() {
        return effectiveFrom;
    }

    public Instant getEffectiveTo() {
        return effectiveTo;
    }
}
