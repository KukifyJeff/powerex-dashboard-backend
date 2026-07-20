package com.chng.powerexdashboardbackend.responses.home;

import lombok.Data;

@Data
public class HomeDataStatus {
    private Double spotCoveragePercent;
    private String latestSpotDate;
    private String latestLongtermPeriod;
    private Integer missingRecordCount;
}
