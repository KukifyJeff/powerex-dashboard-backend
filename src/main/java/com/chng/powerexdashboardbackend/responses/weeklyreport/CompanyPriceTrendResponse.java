package com.chng.powerexdashboardbackend.responses.weeklyreport;

import com.chng.powerexdashboardbackend.dto.weeklyreport.ChartSeriesDTO;
import com.chng.powerexdashboardbackend.dto.weeklyreport.CompanyPriceTrendWeekDTO;
import lombok.Data;

import java.util.List;

@Data
public class CompanyPriceTrendResponse {
    private String unit;
    private String weekRule;
    private String maxWeekKey;
    private String selectedLastDataWeekKey;
    private List<String> xAxis;
    private List<ChartSeriesDTO> series;
    private List<CompanyPriceTrendWeekDTO> weeks;
}
