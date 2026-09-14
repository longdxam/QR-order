package com.qros.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

/**
 * Máy trạng thái khoá luỹ tiến của {@code FR-AUTH-03}, tách khỏi HTTP/CSDL — xem
 * {@code security/CredentialStuffingTest} cho phiên bản chạy qua HTTP thật trên PostgreSQL
 * ({@code TM-AUTH-01}).
 */
class AppUserTest {

    private static final Instant T0 = Instant.parse("2026-09-14T00:00:00Z");

    @Test
    void chuaDuNguong_khongKhoa() {
        AppUser user = new AppUser();

        for (int lan = 1; lan <= 4; lan++) {
            boolean vuaBiKhoa = user.registerFailedAttempt(T0.plusSeconds(lan));
            assertThat(vuaBiKhoa).isFalse();
        }
        assertThat(user.isLocked(T0.plusSeconds(5))).isFalse();
    }

    @Test
    void lanSaiThuNam_trongCuaSo15Phut_khoa15Phut() {
        AppUser user = new AppUser();
        Instant now = T0;
        for (int lan = 1; lan <= 4; lan++) {
            now = now.plus(Duration.ofMinutes(1));
            user.registerFailedAttempt(now);
        }
        now = now.plus(Duration.ofMinutes(1));
        boolean vuaBiKhoa = user.registerFailedAttempt(now);

        assertThat(vuaBiKhoa).isTrue();
        assertThat(user.isLocked(now)).isTrue();
        assertThat(user.isLocked(now.plus(Duration.ofMinutes(15)).minusSeconds(1))).isTrue();
        assertThat(user.isLocked(now.plus(Duration.ofMinutes(15)).plusSeconds(1))).isFalse();
    }

    @Test
    void saiQuaCachXaNhau_khongDonDich_khongKhoa() {
        AppUser user = new AppUser();
        user.registerFailedAttempt(T0);
        user.registerFailedAttempt(T0.plus(Duration.ofMinutes(20))); // ngoài cửa sổ 15 phút
        user.registerFailedAttempt(T0.plus(Duration.ofMinutes(40)));
        user.registerFailedAttempt(T0.plus(Duration.ofMinutes(60)));

        // Mỗi lần đều bắt đầu lại cửa sổ mới nên chưa bao giờ chạm ngưỡng 5.
        assertThat(user.isLocked(T0.plus(Duration.ofMinutes(60)))).isFalse();
    }

    @Test
    void khoaLanHai_luyTienGapDoi() {
        AppUser user = new AppUser();
        Instant now = lockOnce(user, T0);
        assertThat(user.isLocked(now)).isTrue();

        // Qua khỏi lần khoá đầu (15 phút) rồi mới sai tiếp — cửa sổ dồn lỗi được reset tự nhiên,
        // nhưng lockoutCount thì không.
        now = now.plus(Duration.ofMinutes(16));
        assertThat(user.isLocked(now)).isFalse();
        Instant lockedAgainAt = lockOnce(user, now);

        assertThat(user.isLocked(lockedAgainAt)).isTrue();
        assertThat(user.isLocked(lockedAgainAt.plus(Duration.ofMinutes(30)).minusSeconds(1))).isTrue();
        assertThat(user.isLocked(lockedAgainAt.plus(Duration.ofMinutes(30)).plusSeconds(1))).isFalse();
    }

    @Test
    void dangNhapThanhCong_xoaSachLuyTien() {
        AppUser user = new AppUser();
        Instant lockedAt = lockOnce(user, T0);
        Instant unlockedAt = lockedAt.plus(Duration.ofMinutes(16));

        user.registerSuccessfulLogin();

        // Lần khoá kế tiếp phải quay lại 15 phút (lockoutCount về 0), không phải 30 phút.
        Instant lockedAgainAt = lockOnce(user, unlockedAt);
        assertThat(user.isLocked(lockedAgainAt.plus(Duration.ofMinutes(15)).minusSeconds(1))).isTrue();
        assertThat(user.isLocked(lockedAgainAt.plus(Duration.ofMinutes(15)).plusSeconds(1))).isFalse();
    }

    /** Gây đúng 5 lần sai liên tiếp cách nhau 1 phút, trả về thời điểm lần sai thứ năm. */
    private static Instant lockOnce(AppUser user, Instant start) {
        Instant now = start;
        for (int lan = 1; lan <= 5; lan++) {
            now = now.plus(Duration.ofMinutes(1));
            user.registerFailedAttempt(now);
        }
        return now;
    }
}
