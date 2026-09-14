package com.qros.shared.money;

import java.util.Objects;

/**
 * Số tiền, luôn là số nguyên đơn vị nhỏ nhất của {@link Currency} (với VND là đồng).
 *
 * <p>Bất biến số 2 của repo: không {@code double}, không {@code float}, không
 * {@code BigDecimal} cho tiền ở bất kỳ đâu — kể cả khi hiển thị. Cột tiền trong
 * {@code V1__baseline.sql} là {@code bigint} nên kiểu ở đây khớp thẳng với lược đồ.
 *
 * <p>Không có phép chia: chia hoá đơn ({@code FR-CUS-15}) còn ở {@code OPEN-06} và chưa được
 * mô hình hoá, nên quy tắc làm tròn phần dư chưa được chốt. Thêm phép chia trước khi chốt
 * quy tắc đó sẽ đẻ ra sai lệch một đồng không ai giải thích được.
 *
 * <p>Tham chiếu: {@code FR-CUS-08}, {@code ADR-06}.
 */
public record Money(long amount, Currency currency) implements Comparable<Money> {

    /** Không đồng — điểm bắt đầu quen thuộc cho mọi phép cộng dồn. */
    public static final Money ZERO_VND = new Money(0L, Currency.VND);

    public Money {
        Objects.requireNonNull(currency, "currency không được null");
    }

    public static Money of(long amount, Currency currency) {
        return new Money(amount, currency);
    }

    public static Money ofVnd(long amount) {
        return new Money(amount, Currency.VND);
    }

    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(Math.addExact(amount, other.amount), currency);
    }

    public Money minus(Money other) {
        requireSameCurrency(other);
        return new Money(Math.subtractExact(amount, other.amount), currency);
    }

    /** Nhân với số lượng nguyên — dùng cho dòng đơn: đơn giá × số lượng. */
    public Money times(long factor) {
        return new Money(Math.multiplyExact(amount, factor), currency);
    }

    public Money negated() {
        return new Money(Math.negateExact(amount), currency);
    }

    public boolean isZero() {
        return amount == 0L;
    }

    public boolean isPositive() {
        return amount > 0L;
    }

    public boolean isNegative() {
        return amount < 0L;
    }

    /**
     * Chốt bất biến tại chỗ cho các trường không bao giờ được âm (giá, tổng đơn, số tiền thu).
     * Ràng buộc {@code CHECK (... >= 0)} trong lược đồ là lưới an toàn cuối, không phải lưới đầu.
     */
    public Money requireNonNegative() {
        if (isNegative()) {
            throw new IllegalArgumentException("Số tiền không được âm: " + this);
        }
        return this;
    }

    @Override
    public int compareTo(Money other) {
        requireSameCurrency(other);
        return Long.compare(amount, other.amount);
    }

    @Override
    public String toString() {
        return amount + " " + currency;
    }

    private void requireSameCurrency(Money other) {
        Objects.requireNonNull(other, "other không được null");
        if (currency != other.currency) {
            throw new IllegalArgumentException(
                    "Không cộng trừ được hai đơn vị tiền khác nhau: %s và %s"
                            .formatted(currency, other.currency));
        }
    }
}
