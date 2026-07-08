package com.chng.powerexdashboardbackend.enums;

import java.util.Arrays;

public enum TransactionTypeEnum {

    DIRECT(1, "直接"),
    OUTSOURCED(2, "外送"),
    AGENT_PURCHASE(3, "代理购电"),
    GENERATION_RIGHT_TRANSFER(4, "发电权转让");

    private final int id;
    private final String name;

    TransactionTypeEnum(int id, String name) {
        this.id = id;
        this.name = name;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public static TransactionTypeEnum of(int id) {
        return Arrays.stream(values())
                .filter(e -> e.id == id)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Invalid TransactionType id: " + id));
    }
}