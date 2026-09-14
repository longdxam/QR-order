package com.qros.catalog.domain;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import com.qros.shared.id.UuidV7;

/**
 * Xưởng fixture cho test: {@link MenuVariant}/{@link PriceSchedule} không có setter, chỉ có
 * constructor gói hẹp trong package — đây là lối vào hợp lệ duy nhất từ test, cùng chỗ với chính
 * entity (mẫu {@code AppUserFixture}).
 */
public final class CatalogFixtures {

    private CatalogFixtures() {
    }

    public static MenuVariant bienThe(UUID menuItemId, String name, long priceAmount) {
        return new MenuVariant(UuidV7.generate(), menuItemId, name, priceAmount, true);
    }

    public static PriceSchedule lichGia(UUID menuVariantId, long priceAmount, List<Integer> daysOfWeek,
            LocalTime startsAt, LocalTime endsAt, Instant effectiveFrom, Instant effectiveTo) {
        return new PriceSchedule(UuidV7.generate(), menuVariantId, priceAmount, daysOfWeek,
                startsAt, endsAt, effectiveFrom, effectiveTo);
    }
}
