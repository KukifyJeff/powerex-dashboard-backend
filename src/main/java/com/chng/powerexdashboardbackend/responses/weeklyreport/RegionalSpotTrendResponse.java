package com.chng.powerexdashboardbackend.responses.weeklyreport;

import com.chng.powerexdashboardbackend.dto.weeklyreport.ChartSeriesDTO;
import com.chng.powerexdashboardbackend.dto.weeklyreport.RegionalSpotTrendCompanyDTO;
import lombok.Data;

import java.util.List;

@Data
public class RegionalSpotTrendResponse {
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
