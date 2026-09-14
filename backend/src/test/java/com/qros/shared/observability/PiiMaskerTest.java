package com.qros.shared.observability;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PiiMaskerTest {

    @Test
    void cheMatKhauDangKeyValue() {
        assertThat(PiiMasker.mask("login failed password=hunter2 for user"))
                .doesNotContain("hunter2").contains("password=***");
    }

    @Test
    void cheMatKhauDangJson() {
        assertThat(PiiMasker.mask("{\"email\":\"a@b.com\",\"password\":\"hunter2\"}"))
                .doesNotContain("hunter2");
    }

    @Test
    void cheToken() {
        assertThat(PiiMasker.mask("Authorization: Bearer token=abcXYZ123"))
                .doesNotContain("abcXYZ123");
    }

    @Test
    void cheJwt() {
        String jwt = "eyJhbGciOiJFZERTQSJ9.eyJzdWIiOiJ4In0.c2ln-that-du-dai-de-khop-regex";
        assertThat(PiiMasker.mask("cookie qros_session=" + jwt)).doesNotContain(jwt);
    }

    @Test
    void cheEmail() {
        assertThat(PiiMasker.mask("khách hàng lien.he@qros.test vừa đặt món"))
                .doesNotContain("lien.he@qros.test");
    }

    @Test
    void cheSoTheDaiHon13So() {
        assertThat(PiiMasker.mask("thẻ 4111 1111 1111 1111 hết hạn"))
                .doesNotContain("4111 1111 1111 1111");
    }

    @Test
    void vanBanBinhThuong_khongBiDongCham() {
        String vanBanThuong = "Đăng nhập thành công, đơn hàng #42 đã sẵn sàng";
        assertThat(PiiMasker.mask(vanBanThuong)).isEqualTo(vanBanThuong);
    }

    @Test
    void chuoiRong_hoacNull_khongNemLoi() {
        assertThat(PiiMasker.mask("")).isEmpty();
        assertThat(PiiMasker.mask(null)).isNull();
    }
}
