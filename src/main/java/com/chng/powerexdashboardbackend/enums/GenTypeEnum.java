package com.chng.powerexdashboardbackend.enums;

import lombok.Getter;

import java.util.Arrays;

@Getter
public enum GenTypeEnum {

    COAL(1, "煤电"),
    SOLAR(2, "光伏"),
    WIND(3, "风电"),
    GAS(4, "燃气"),
    HYDRO(5, "水电"),
    NUCLEAR(6, "核电");

    private final int id;
    private final String name;

    GenTypeEnum(int id, String name) {
        this.id = id;
        this.name = name;
    }

    public static GenTypeEnum of(int id) {
        return Arrays.stream(values())
                .filter(e -> e.id == id)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Invalid GenType id: " + id));
    }

    public boolean isRenewable() {
        return this == SOLAR || this == WIND || this == HYDRO;
    }

    public boolean isThermal() {
        return this == COAL || this == GAS;
    }
}