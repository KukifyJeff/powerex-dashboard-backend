package com.chng.powerexdashboardbackend.dto.weeklyreport;

import lombok.Data;

import java.util.List;

@Data
public class RegionalSpotTrendResponseDTO {
    private String unit;
    private String weekRule;
    private Integer regionId;
    private String regionName;
    private String maxWeekKey;
    private String selectedLastDataWeekKey;
    private List<String> xAxis;
    private List<ChartSeriesDTO> series;
    private List<RegionalSpotTrendCompanyDTO> companies;
}
