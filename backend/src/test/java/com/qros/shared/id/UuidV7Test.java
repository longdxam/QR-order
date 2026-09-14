package com.qros.shared.id;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;

/** {@code TM-ACC-01} · khoá chính hướng ra ngoài phải có thứ tự và không đoán được. */
class UuidV7Test {

    @Test
    void tmAcc01_dungBoCucRfc9562() {
        UUID id = UuidV7.generate();

        assertThat(id.version()).isEqualTo(7);
        assertThat(id.variant()).isEqualTo(2);
        assertThat(UuidV7.isUuidV7(id)).isTrue();
    }

    @Test
    void tmAcc01_giuLaiMocThoiGianDenTungMiliGiay() {
        Instant moc = Instant.parse("2026-09-13T10:15:30.123Z");
        UuidV7.generate(); // đẩy bộ đếm đơn điệu lên mốc hiện tại trước đã

        // Mốc quá khứ dùng cho fixture và dữ liệu nạp lại phải được giữ nguyên, không bị
        // bộ đếm của luồng sinh ID đang chạy kéo tới hiện tại.
        assertThat(UuidV7.timestampOf(UuidV7.generate(moc))).isEqualTo(moc);
    }

    @Test
    void tmAcc01_tangDanNgayCaTrongCungMotMiliGiay() {
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 5_000; i++) {
            ids.add(UuidV7.generate());
        }

        for (int i = 1; i < ids.size(); i++) {
            assertThat(ids.get(i).getMostSignificantBits())
                    .as("ID thứ %d phải lớn hơn ID trước", i)
                    .isGreaterThan(ids.get(i - 1).getMostSignificantBits());
        }
        assertThat(new HashSet<>(ids)).hasSameSizeAs(ids);
        // Nếu mỗi ID rơi vào một mili giây riêng thì phép thử trên chưa chạm tới bộ đếm.
        assertThat(ids.stream().map(id -> id.getMostSignificantBits() >>> 16).distinct().count())
                .as("phải có nhiều ID chung một mili giây")
                .isLessThan(ids.size());
    }

    @Test
    void tmAcc01_khongTrungKhiNhieuLuongCungSinh() throws Exception {
        int soLuong = 4_000;
        try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
            List<Callable<UUID>> viec = new ArrayList<>();
            for (int i = 0; i < soLuong; i++) {
                viec.add(UuidV7::generate);
            }
            Set<UUID> ids = new HashSet<>();
            for (Future<UUID> ketQua : pool.invokeAll(viec)) {
                ids.add(ketQua.get());
            }
            assertThat(ids).hasSize(soLuong);
        }
    }

    @Test
    void tmAcc01_tuChoiUuidKhongPhaiPhienBan7() {
        UUID v4 = UUID.randomUUID();

        assertThat(UuidV7.isUuidV7(v4)).isFalse();
        assertThat(UuidV7.isUuidV7(null)).isFalse();
        assertThatThrownBy(() -> UuidV7.timestampOf(v4)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UuidV7.generate(-1L)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void tmAcc01_phanNgauNhienThucSuThayDoi() {
        // Hai ID cùng mốc thời gian chỉ được trùng nhau ở 48 bit thời gian, không hơn.
        long moc = Instant.parse("2026-09-13T10:15:30Z").toEpochMilli();

        assertThat(UuidV7.generate(moc).getLeastSignificantBits())
                .isNotEqualTo(UuidV7.generate(moc).getLeastSignificantBits());
    }
}
