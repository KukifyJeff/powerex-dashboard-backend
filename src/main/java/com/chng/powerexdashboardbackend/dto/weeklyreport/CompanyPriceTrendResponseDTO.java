package com.chng.powerexdashboardbackend.dto.weeklyreport;

import lombok.Data;

import java.util.List;

@Data
public class CompanyPriceTrendResponseDTO {
    private String unit;
    private String weekRule;
    private String maxWeekKey;
    private String selectedLastDataWeekKey;
    private List<String> xAxis;
    private List<ChartSeriesDTO> series;
    private List<CompanyPriceTrendWeekDTO> weeks;
}
