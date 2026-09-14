package com.qros.catalog.domain;

import java.math.BigDecimal;

/** Cách viết đúng: tiền là long, còn số thực chỉ dành cho đại lượng không phải tiền. */
public class FixtureIntegerMoney {
    public long priceAmount;
    public BigDecimal currentStock;
}
