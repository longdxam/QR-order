package com.qros.shared.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

/** {@code FR-CUS-08} · bất biến số 2: tiền là số nguyên đơn vị đồng. */
class MoneyTest {

    @Test
    void frCus08_moneyApiKhongChamToiKieuSoThuc() {
        // Bất biến chỉ có giá trị nếu API không mở sẵn cửa hậu cho double/float/BigDecimal.
        for (Method method : Money.class.getDeclaredMethods()) {
            assertThat(method.getReturnType()).isNotIn(double.class, float.class, BigDecimal.class);
            assertThat(Arrays.asList(method.getParameterTypes()))
                    .as("tham số của %s", method.getName())
                    .doesNotContain(double.class, float.class, BigDecimal.class);
        }
        assertThat(Money.class.getRecordComponents()[0].getType()).isEqualTo(long.class);
    }

    @Test
    void frCus08_congTruNhanGiuNguyenDonVi() {
        Money donGia = Money.ofVnd(55_000L);

        assertThat(donGia.times(3L)).isEqualTo(Money.ofVnd(165_000L));
        assertThat(donGia.plus(Money.ofVnd(5_000L))).isEqualTo(Money.ofVnd(60_000L));
        assertThat(donGia.minus(Money.ofVnd(5_000L))).isEqualTo(Money.ofVnd(50_000L));
        assertThat(Money.ZERO_VND.plus(donGia)).isEqualTo(donGia);
    }

    @Test
    void frCus08_khongTronHaiDonViTienKhacNhau() {
        // Hôm nay chỉ có VND, nhưng phép cộng phải sai ngay từ khi có đơn vị thứ hai.
        Money vnd = Money.ofVnd(1_000L);

        assertThat(Currency.values()).containsExactly(Currency.VND);
        assertThatThrownBy(() -> vnd.plus(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void frCus08_tranSoBaoLoiThayViQuayVong() {
        Money lon = Money.ofVnd(Long.MAX_VALUE);

        assertThatThrownBy(() -> lon.plus(Money.ofVnd(1L))).isInstanceOf(ArithmeticException.class);
        assertThatThrownBy(() -> lon.times(2L)).isInstanceOf(ArithmeticException.class);
        assertThatThrownBy(() -> Money.ofVnd(Long.MIN_VALUE).negated())
                .isInstanceOf(ArithmeticException.class);
    }

    @Test
    void frCus08_soAmBiChanOChoDuocKhaiBaoLaKhongAm() {
        assertThat(Money.ofVnd(0L).requireNonNegative()).isEqualTo(Money.ZERO_VND);
        assertThat(Money.ofVnd(-1L).isNegative()).isTrue();
        assertThatThrownBy(() -> Money.ofVnd(-1L).requireNonNegative())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void frCus08_soSanhTheoGiaTri() {
        assertThat(Money.ofVnd(1_000L)).isLessThan(Money.ofVnd(2_000L));
        assertThat(Money.ofVnd(2_000L)).isEqualByComparingTo(Money.ofVnd(2_000L));
        assertThat(Money.ofVnd(55_000L)).hasToString("55000 VND");
    }
}
