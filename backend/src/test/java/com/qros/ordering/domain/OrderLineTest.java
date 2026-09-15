package com.qros.ordering.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.qros.shared.error.ErrorCode;
import com.qros.shared.error.QrosException;

class OrderLineTest {

    private static final Instant NOW = Instant.parse("2026-09-15T03:00:00Z");

    @Test
    void chiChoPhepTienTungBuoc() {
        OrderLine line = lineMoi();

        line.chuyenTrangThai("CONFIRMED", null, NOW);
        line.chuyenTrangThai("PREPARING", null, NOW.plusSeconds(1));
        line.chuyenTrangThai("READY", null, NOW.plusSeconds(61));
        line.chuyenTrangThai("SERVED", null, NOW.plusSeconds(62));

        assertThat(line.getStatus()).isEqualTo("SERVED");
    }

    @Test
    void tuChoiNhayCoc() {
        assertThatThrownBy(() -> lineMoi().chuyenTrangThai("READY", null, NOW))
                .isInstanceOfSatisfying(QrosException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_TRANSITION));
    }

    @Test
    void huyThangTrangThaiDangXuLyNhungBatBuocLyDo() {
        OrderLine line = lineMoi();
        line.chuyenTrangThai("CONFIRMED", null, NOW);
        line.chuyenTrangThai("PREPARING", null, NOW);

        assertThatThrownBy(() -> line.chuyenTrangThai("CANCELLED", " ", NOW))
                .isInstanceOfSatisfying(QrosException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.REASON_REQUIRED));

        line.chuyenTrangThai("CANCELLED", "Khách đổi món", NOW);
        assertThat(line.getStatus()).isEqualTo("CANCELLED");
    }

    private static OrderLine lineMoi() {
        return OrderLine.moi(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "Cà phê",
                "Vừa", 35_000, 1, null, null, "BAR");
    }
}
