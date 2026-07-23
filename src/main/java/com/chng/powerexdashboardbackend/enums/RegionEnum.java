package com.chng.powerexdashboardbackend.enums;

import lombok.Getter;

import java.util.Arrays;

@Getter
public enum RegionEnum {

    NORTHEAST(1, "东北"),
    NORTHWEST(2, "西北"),
    NORTH_CHINA(3, "华北"),
    CENTRAL_CHINA(4, "华中"),
    EAST_CHINA(5, "华东"),
    SOUTHERN(6, "南方"),
    SOUTHWEST(7, "西南"),
    NEW_ENERGY(8, "新能源");

    private final int id;
    private final String name;

    RegionEnum(int id, String name) {
        this.id = id;
        this.name = name;
    }

    // 🔥 防错工具方法（非常重要）
    public static RegionEnum of(int id) {
        return Arrays.stream(values())
                .filter(e -> e.id == id)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Invalid Region id: " + id));
    }
}