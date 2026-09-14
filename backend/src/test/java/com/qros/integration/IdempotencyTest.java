package com.qros.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.qros.shared.error.ErrorCode;
import com.qros.shared.error.QrosException;
import com.qros.shared.idempotency.IdempotencyGuard;
import com.qros.shared.idempotency.IdempotencyProperties;
import com.qros.shared.idempotency.IdempotencyRepository;
import com.qros.shared.idempotency.IdempotencyRequest;
import com.qros.shared.idempotency.IdempotencySweeper;
import com.qros.shared.idempotency.IdempotentResponse;

/**
 * Khoá idempotency chạy trên PostgreSQL thật — {@code FR-CUS-09}, {@code TM-ORD-02}.
 *
 * <p>Phần đáng kiểm nhất ở đây không phải đường thẳng mà là ba nhánh lệch: gửi lặp khác nội dung,
 * hai yêu cầu song song, và tác dụng phụ hỏng giữa chừng. Cả ba đều là hành vi của cơ sở dữ liệu
 * dưới tải, nên không mock được — chúng chỉ hiện ra trên PostgreSQL thật.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "qros.idempotency.sweeper-enabled=false")
class IdempotencyTest extends QrosIntegrationTest {

    private static final String SCOPE = "guest.createOrder";

    @Autowired
    private IdempotencyGuard guard;

    @Autowired
    private IdempotencyRepository repository;

    @Autowired
    private IdempotencyProperties properties;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private DongHoChinhDuoc dongHo;

    private TransactionTemplate giaoDich;

    @BeforeEach
    void chuanBi() {
        giaoDich = new TransactionTemplate(transactionManager);
        repository.deleteAll();
    }

    @Test
    void frCus09_cungKhoaVaCungNoiDungTraLaiKetQuaCu() {
        UUID key = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        AtomicInteger soLanChay = new AtomicInteger();

        IdempotentResponse<String> lanDau = chay(key, sessionId, "{\"items\":1}", soLanChay, "DON-001");
        IdempotentResponse<String> lanHai = chay(key, sessionId, "{\"items\":1}", soLanChay, "DON-002");

        assertThat(lanDau.status()).isEqualTo(201);
        assertThat(lanDau.body()).isEqualTo("DON-001");
        // Lần gửi lại nhận đúng đơn cũ, không phải đơn mới mà action vừa định tạo.
        assertThat(lanHai.status()).isEqualTo(201);
        assertThat(lanHai.body()).isEqualTo("DON-001");
        assertThat(soLanChay).hasValue(1);
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void tmOrd02_cungKhoaKhacNoiDungThiBaoLoi() {
        UUID key = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        AtomicInteger soLanChay = new AtomicInteger();
        chay(key, sessionId, "{\"items\":1}", soLanChay, "DON-001");

        assertThatThrownBy(() -> chay(key, sessionId, "{\"items\":99}", soLanChay, "DON-002"))
                .isInstanceOf(QrosException.class)
                .extracting(loi -> ((QrosException) loi).errorCode())
                .isEqualTo(ErrorCode.IDEMPOTENCY_KEY_REUSED);

        assertThat(soLanChay).hasValue(1);
    }

    @Test
    void tmOrd02_khoaCuaPhienKhacKhongDocDuocKetQua() {
        UUID key = UUID.randomUUID();
        AtomicInteger soLanChay = new AtomicInteger();
        chay(key, UUID.randomUUID(), "{\"items\":1}", soLanChay, "DON-001");

        // Cùng thân yêu cầu nhưng khác phiên: phải là cùng một lỗi với ca khác nội dung, để không
        // có tín hiệu nào phân biệt "khoá của người khác" với "nội dung sai" (bất biến số 7).
        assertThatThrownBy(() -> chay(key, UUID.randomUUID(), "{\"items\":1}", soLanChay, "DON-002"))
                .isInstanceOf(QrosException.class)
                .extracting(loi -> ((QrosException) loi).errorCode())
                .isEqualTo(ErrorCode.IDEMPOTENCY_KEY_REUSED);

        assertThat(soLanChay).hasValue(1);
    }

    @Test
    void tmOrd02_tamYeuCauSongSongChiSinhMotHieuUng() throws Exception {
        UUID key = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        AtomicInteger soLanChay = new AtomicInteger();
        int soLuong = 8;
        CyclicBarrier vach = new CyclicBarrier(soLuong);

        List<String> ketQua = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(soLuong)) {
            List<Callable<String>> viec = new ArrayList<>();
            for (int i = 0; i < soLuong; i++) {
                String maDon = "DON-%02d".formatted(i);
                viec.add(() -> {
                    vach.await();
                    return chay(key, sessionId, "{\"items\":1}", soLanChay, maDon).body();
                });
            }
            for (Future<String> future : pool.invokeAll(viec)) {
                ketQua.add(future.get());
            }
        }

        assertThat(soLanChay).as("chỉ một lượt được chạy tác dụng phụ").hasValue(1);
        assertThat(ketQua).hasSize(soLuong).containsOnly(ketQua.getFirst());
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void frCus09_nghiepVuHongThiKhoaKhongBiChiemCho() {
        UUID key = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        AtomicInteger soLanChay = new AtomicInteger();

        assertThatThrownBy(() -> giaoDich.execute(status -> guard.execute(
                new IdempotencyRequest(key, SCOPE, sessionId, "{\"items\":1}"), String.class,
                () -> {
                    soLanChay.incrementAndGet();
                    throw new IllegalStateException("hết nguyên liệu giữa chừng");
                }))).isInstanceOf(IllegalStateException.class);

        // Khoá phải biến mất cùng tác dụng phụ, nếu không khách bị chặn đặt món suốt 24 giờ.
        assertThat(repository.count()).isZero();

        IdempotentResponse<String> thuLai = chay(key, sessionId, "{\"items\":1}", soLanChay, "DON-001");

        assertThat(thuLai.body()).isEqualTo("DON-001");
        assertThat(soLanChay).hasValue(2);
    }

    @Test
    void frCus09_khoaQuaHanDuocDungLai() {
        UUID key = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        AtomicInteger soLanChay = new AtomicInteger();
        chay(key, sessionId, "{\"items\":1}", soLanChay, "DON-001");

        dongHo.tien(Duration.ofHours(25));

        // Ngoài cửa sổ 24 giờ, cùng khoá là một yêu cầu mới chứ không phải một lần thử lại.
        IdempotentResponse<String> sauKhiHetHan =
                chay(key, sessionId, "{\"items\":1}", soLanChay, "DON-002");

        assertThat(sauKhiHetHan.body()).isEqualTo("DON-002");
        assertThat(soLanChay).hasValue(2);
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void frCus09_donDepXoaKhoaHetHan(@Autowired IdempotencySweeper sweeper) {
        UUID key = UUID.randomUUID();
        chay(key, UUID.randomUUID(), "{\"items\":1}", new AtomicInteger(), "DON-001");
        chay(UUID.randomUUID(), UUID.randomUUID(), "{\"items\":1}", new AtomicInteger(), "DON-002");
        dongHo.tien(Duration.ofHours(25));
        // Khoá thứ hai được tạo sau khi tua nên vẫn còn hạn; chỉ khoá cũ bị dọn.
        chay(UUID.randomUUID(), UUID.randomUUID(), "{\"items\":1}", new AtomicInteger(), "DON-003");

        assertThat(sweeper.sweepOnce()).isEqualTo(2);

        assertThat(repository.findById(key)).isEmpty();
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void adr05_tuChoiChayNgoaiGiaoDich() {
        assertThatThrownBy(() -> guard.execute(
                new IdempotencyRequest(UUID.randomUUID(), SCOPE, null, "{}"), String.class,
                () -> new IdempotentResponse<>(201, "DON-001")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("giao dịch");

        assertThat(repository.count()).isZero();
    }

    @Test
    void frCus09_giuKetQua24Gio() {
        assertThat(properties.retention()).isEqualTo(Duration.ofHours(24));
    }

    private IdempotentResponse<String> chay(UUID key, UUID sessionId, String body,
            AtomicInteger soLanChay, String ketQua) {

        return giaoDich.execute(status -> guard.execute(
                new IdempotencyRequest(key, SCOPE, sessionId, body), String.class,
                () -> {
                    soLanChay.incrementAndGet();
                    return new IdempotentResponse<>(201, ketQua);
                }));
    }

    /** Đồng hồ chỉnh được, để tua qua cửa sổ 24 giờ thay vì chờ 24 giờ. */
    static final class DongHoChinhDuoc extends Clock {

        private volatile Instant hienTai = Instant.now();

        void tien(Duration khoang) {
            hienTai = hienTai.plus(khoang);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return hienTai;
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class CauHinhKiemTra {

        @Bean
        @Primary
        DongHoChinhDuoc dongHoKiemTra() {
            return new DongHoChinhDuoc();
        }
    }
}
