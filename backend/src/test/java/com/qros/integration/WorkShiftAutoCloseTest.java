package com.qros.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import com.qros.identity.domain.AppUser;
import com.qros.identity.domain.AppUserFixture;
import com.qros.identity.domain.WorkShift;
import com.qros.identity.repository.AppUserRepository;
import com.qros.identity.repository.WorkShiftRepository;
import com.qros.identity.service.WorkShiftAutoCloser;
import com.qros.shared.id.UuidV7;

/**
 * {@code FR-AUTH-04}: "Phiên tự đóng khi kết ca hoặc sau 12 giờ".
 *
 * <p>Mở ca bằng PIN trên thiết bị đã đăng ký chưa có endpoint ({@code OPEN-08}) nên test này dựng
 * hàng {@link WorkShift} thẳng qua {@link WorkShift#open}/{@link WorkShift#closed} — đúng dữ liệu
 * một endpoint mở ca thật sẽ tạo ra, chỉ khác cách tạo. Chỉ bảng {@code store} chưa có entity
 * (module {@code venue} thuộc {@code BL-M1-01}) nên phải chèn qua JDBC.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class WorkShiftAutoCloseTest extends QrosIntegrationTest {

    @Autowired
    private WorkShiftAutoCloser autoCloser;

    @Autowired
    private WorkShiftRepository workShiftRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DongHoChinhDuoc dongHo;

    private UUID userId;
    private UUID storeId;

    @BeforeEach
    void chuanBi() {
        // work_shift không có ON DELETE CASCADE trực tiếp từ ràng buộc đặt tên (xem
        // V4__work_shift_cascade.sql) nên dọn work_shift trước app_user, đúng thứ tự FK.
        workShiftRepository.deleteAll();
        appUserRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM store");

        storeId = UuidV7.generate();
        jdbcTemplate.update("INSERT INTO store (id, code, name) VALUES (?, ?, ?)",
                storeId, "TEST-" + storeId, "Chi nhánh kiểm thử");

        AppUser user = AppUserFixture.moi("shift-" + UUID.randomUUID() + "@qros.test", null);
        appUserRepository.save(user);
        userId = user.getId();
    }

    @Test
    void caMoQua12Gio_biTuDongDong() {
        WorkShift shift = WorkShift.open(userId, storeId, dongHo.instant().minus(Duration.ofHours(13)));
        workShiftRepository.save(shift);

        int daDong = autoCloser.closeExpiredOnce();

        assertThat(daDong).isEqualTo(1);
        assertThat(workShiftRepository.findById(shift.getId()).orElseThrow().isOpen()).isFalse();
    }

    @Test
    void caMoDuoi12Gio_chuaBiDong() {
        WorkShift shift = WorkShift.open(userId, storeId, dongHo.instant().minus(Duration.ofHours(11)));
        workShiftRepository.save(shift);

        int daDong = autoCloser.closeExpiredOnce();

        assertThat(daDong).isEqualTo(0);
        assertThat(workShiftRepository.findById(shift.getId()).orElseThrow().isOpen()).isTrue();
    }

    @Test
    void caDaDongRoi_khongBiDemLai() {
        WorkShift shift = WorkShift.closed(userId, storeId,
                dongHo.instant().minus(Duration.ofHours(20)), dongHo.instant().minus(Duration.ofHours(1)));
        workShiftRepository.save(shift);

        int daDong = autoCloser.closeExpiredOnce();

        assertThat(daDong).isEqualTo(0);
    }

    /** Đồng hồ chỉnh được — cùng lối với {@code IdempotencyTest}/{@code CredentialStuffingTest}. */
    static final class DongHoChinhDuoc extends Clock {
        private volatile Instant hienTai = Instant.now();

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
