package com.qros.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.qros.catalog.domain.CatalogFixtures;
import com.qros.catalog.domain.MenuVariant;
import com.qros.catalog.domain.PriceSchedule;
import com.qros.shared.id.UuidV7;

/** {@code FR-MGT-04}: giá hiệu lực tại một thời điểm — thuần Java, không cần Spring/CSDL. */
class EffectivePricingTest {

    private static final UUID ITEM_ID = UuidV7.generate();
    private static final Instant NOW = Instant.parse("2026-09-14T10:00:00Z"); // Thứ Hai, 14/09/2026

    @Test
    void khongCoLichNao_dungGiaMacDinhCuaBienThe() {
        MenuVariant variant = CatalogFixtures.bienThe(ITEM_ID, "Size M", 45_000);

        long gia = EffectivePricing.resolve(variant, List.of(), NOW, ZoneOffset.UTC);

        assertThat(gia).isEqualTo(45_000);
    }

    @Test
    void lichDangHieuLuc_dungGiaLich() {
        MenuVariant variant = CatalogFixtures.bienThe(ITEM_ID, "Size M", 45_000);
        PriceSchedule lich = CatalogFixtures.lichGia(variant.getId(), 35_000, allDays(),
                null, null, NOW.minus(1, ChronoUnit.DAYS), null);

        long gia = EffectivePricing.resolve(variant, List.of(lich), NOW, ZoneOffset.UTC);

        assertThat(gia).isEqualTo(35_000);
    }

    @Test
    void lichChuaToiHieuLuc_bqueGiaMacDinh() {
        MenuVariant variant = CatalogFixtures.bienThe(ITEM_ID, "Size M", 45_000);
        PriceSchedule lichTuongLai = CatalogFixtures.lichGia(variant.getId(), 35_000, allDays(),
                null, null, NOW.plus(1, ChronoUnit.DAYS), null);

        long gia = EffectivePricing.resolve(variant, List.of(lichTuongLai), NOW, ZoneOffset.UTC);

        assertThat(gia).isEqualTo(45_000);
    }

    @Test
    void lichDaHetHieuLuc_bueGiaMacDinh() {
        MenuVariant variant = CatalogFixtures.bienThe(ITEM_ID, "Size M", 45_000);
        PriceSchedule lichCu = CatalogFixtures.lichGia(variant.getId(), 35_000, allDays(),
                null, null, NOW.minus(10, ChronoUnit.DAYS), NOW.minus(1, ChronoUnit.DAYS));

        long gia = EffectivePricing.resolve(variant, List.of(lichCu), NOW, ZoneOffset.UTC);

        assertThat(gia).isEqualTo(45_000);
    }

    @Test
    void saiThuTrongTuan_bueGiaMacDinh() {
        MenuVariant variant = CatalogFixtures.bienThe(ITEM_ID, "Size M", 45_000);
        // NOW là thứ Hai (1) — lịch chỉ áp Chủ nhật (7).
        PriceSchedule lichCuoiTuan = CatalogFixtures.lichGia(variant.getId(), 35_000, List.of(7),
                null, null, NOW.minus(1, ChronoUnit.DAYS), null);

        long gia = EffectivePricing.resolve(variant, List.of(lichCuoiTuan), NOW, ZoneOffset.UTC);

        assertThat(gia).isEqualTo(45_000);
    }

    @Test
    void ngoaiKhungGioTrongNgay_bueGiaMacDinh() {
        MenuVariant variant = CatalogFixtures.bienThe(ITEM_ID, "Size M", 45_000);
        // NOW = 10:00 UTC — lịch chỉ áp 14:00–17:00 ("giờ vàng buổi chiều").
        PriceSchedule lichGioVang = CatalogFixtures.lichGia(variant.getId(), 35_000, allDays(),
                LocalTime.of(14, 0), LocalTime.of(17, 0), NOW.minus(1, ChronoUnit.DAYS), null);

        long gia = EffectivePricing.resolve(variant, List.of(lichGioVang), NOW, ZoneOffset.UTC);

        assertThat(gia).isEqualTo(45_000);
    }

    @Test
    void trongKhungGioTrongNgay_dungGiaLich() {
        MenuVariant variant = CatalogFixtures.bienThe(ITEM_ID, "Size M", 45_000);
        PriceSchedule lichGioVang = CatalogFixtures.lichGia(variant.getId(), 35_000, allDays(),
                LocalTime.of(9, 0), LocalTime.of(11, 0), NOW.minus(1, ChronoUnit.DAYS), null);

        long gia = EffectivePricing.resolve(variant, List.of(lichGioVang), NOW, ZoneOffset.UTC);

        assertThat(gia).isEqualTo(35_000);
    }

    @Test
    void nhieuLichChongLan_dungLichMoiNhat() {
        MenuVariant variant = CatalogFixtures.bienThe(ITEM_ID, "Size M", 45_000);
        PriceSchedule lichCu = CatalogFixtures.lichGia(variant.getId(), 35_000, allDays(),
                null, null, NOW.minus(10, ChronoUnit.DAYS), null);
        PriceSchedule lichMoi = CatalogFixtures.lichGia(variant.getId(), 30_000, allDays(),
                null, null, NOW.minus(1, ChronoUnit.DAYS), null);

        long gia = EffectivePricing.resolve(variant, List.of(lichCu, lichMoi), NOW, ZoneOffset.UTC);

        assertThat(gia).isEqualTo(30_000);
    }

    private static List<Integer> allDays() {
        return List.of(1, 2, 3, 4, 5, 6, 7);
    }
}
