package com.chng.powerexdashboardbackend.responses.weeklyreport;

import com.chng.powerexdashboardbackend.dto.weeklyreport.ChartSeriesDTO;
import com.chng.powerexdashboardbackend.dto.weeklyreport.ProvincialSpotTrendWeekDTO;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class ProvincialSpotTrendResponse {
    private String unit;
    private String weekRule;
    private String maxWeekKey;
    private String selectedLastDataWeekKey;
    private BigDecimal annualCumulativeMarketAvgPrice;
    private List<String> xAxis;
    private List<ChartSeriesDTO> series;
    private List<ProvincialSpotTrendWeekDTO> weeks;
}
