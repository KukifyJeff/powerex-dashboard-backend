package com.chng.powerexdashboardbackend.enums;

import java.util.Arrays;

public enum TransactionPeriodEnum {

    YEARLY(1, "年度"),
    YEARLY_DECOMPOSED(2, "年度分解"),
    MULTI_MONTH(3, "多月"),
    MULTI_MONTH_DECOMPOSED(4, "多月分解"),
    MONTHLY(5, "月度"),
    MONTHLY_DECOMPOSED(6, "月度分解");

    private final int id;
    private final String name;

    TransactionPeriodEnum(int id, String name) {
        this.id = id;
        this.name = name;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public static TransactionPeriodEnum of(int id) {
        return Arrays.stream(values())
                .filter(e -> e.id == id)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Invalid TransactionPeriod id: " + id));
    }

    // 🔥 常用判断（电力分析很重要）
    public boolean isYearly() {
        return this == YEARLY || this == YEARLY_DECOMPOSED;
    }

    public boolean isMonthly() {
        return this == MONTHLY || this == MONTHLY_DECOMPOSED || this == MULTI_MONTH || this == MULTI_MONTH_DECOMPOSED;
    }
}