package com.chng.powerexdashboardbackend.dto.home;

import lombok.Data;

@Data
public class HomeLongtermAssetStatDTO {
    private String minPeriod;
    private String maxPeriod;
    private Long recordCount;
}
