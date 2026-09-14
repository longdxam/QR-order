package com.qros.shared.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

/** Cách ly kênh theo phạm vi người nghe — {@code TM-ACC-01}, {@code FR-BAR-02}. */
class OutboxChannelsTest {

    private static final UUID STORE_ID = UUID.fromString("0198f0a1-4b2c-7def-8123-456789abcdef");
    private static final UUID SESSION_ID = UUID.fromString("0198f0a1-4b2c-7def-8123-000000000001");

    @Test
    void tmAcc01_phienBanThangChiNhanhKhiCoCaHai() {
        // Kênh chi nhánh có mọi nhân viên nghe; sự kiện của một bàn không được lọt vào đó.
        assertThat(OutboxChannels.of(STORE_ID, SESSION_ID)).isEqualTo("qros:session:" + SESSION_ID);
    }

    @Test
    void frBar02_suKienChiNhanhDiVaoKenhChiNhanh() {
        assertThat(OutboxChannels.of(STORE_ID, null)).isEqualTo("qros:store:" + STORE_ID);
    }

    @Test
    void frBar02_suKienKhongThuocPhamViNaoDiVaoKenhChung() {
        assertThat(OutboxChannels.of(null, null)).isEqualTo("qros:global");
    }
}
