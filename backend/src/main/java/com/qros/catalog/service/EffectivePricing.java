package com.qros.catalog.service;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;

import com.qros.catalog.domain.MenuVariant;
import com.qros.catalog.domain.PriceSchedule;

/**
 * {@code FR-MGT-04}: giá hiệu lực tại một thời điểm — biến thể món có 0 hoặc nhiều lịch giá tương
 * lai chồng nhau; giá dùng được là lịch mới nhất mà {@code effectiveFrom} đã tới, còn hiệu lực
 * ({@code effectiveTo} null hoặc trong tương lai), khớp thứ trong tuần, và trong khung giờ nếu có.
 * Không có lịch nào khớp thì dùng {@code menu_variant.price_amount} làm mặc định.
 *
 * <p>Tách khỏi {@link MenuService} để test được thuần Java, không cần Spring/CSDL — logic này
 * chưa có nơi ghi thật (chưa có endpoint quản trị đổi giá, M3) nên càng cần test độc lập với hạ
 * tầng, tách bạch khỏi phần chưa xây được.
 */
final class EffectivePricing {

    private EffectivePricing() {
    }

    static long resolve(MenuVariant variant, List<PriceSchedule> schedulesForVariant, Instant now, ZoneId zone) {
        ZonedDateTime local = now.atZone(zone);
        int isoDayOfWeek = local.getDayOfWeek().getValue(); // 1=Monday..7=Sunday, khớp mặc định CSDL
        LocalTime timeOfDay = local.toLocalTime();

        return schedulesForVariant.stream()
                .filter(schedule -> apDung(schedule, now, isoDayOfWeek, timeOfDay))
                .max(Comparator.comparing(PriceSchedule::getEffectiveFrom))
                .map(PriceSchedule::getPriceAmount)
                .orElse(variant.getPriceAmount());
    }

    private static boolean apDung(PriceSchedule schedule, Instant now, int isoDayOfWeek, LocalTime timeOfDay) {
        if (schedule.getEffectiveFrom().isAfter(now)) {
            return false;
        }
        if (schedule.getEffectiveTo() != null && !schedule.getEffectiveTo().isAfter(now)) {
            return false;
        }
        if (!schedule.getDaysOfWeek().contains(isoDayOfWeek)) {
            return false;
        }
        LocalTime startsAt = schedule.getStartsAt();
        LocalTime endsAt = schedule.getEndsAt();
        if (startsAt != null && endsAt != null) {
            return !timeOfDay.isBefore(startsAt) && timeOfDay.isBefore(endsAt);
        }
        return true;
    }
}
