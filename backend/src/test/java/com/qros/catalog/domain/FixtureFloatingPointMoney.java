package com.qros.catalog.domain;

import java.math.BigDecimal;

/** Vi phạm cố ý, chỉ dùng để chứng minh luật ArchUnit về tiền thực sự chặn. */
public class FixtureFloatingPointMoney {
    public double priceAmount;
    public BigDecimal totalAmount;
}
