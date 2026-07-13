package com.chng.powerexdashboardbackend.dto.weeklyreport;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class ProvincialSpotTrendResponseDTO {
    private String unit;
    private String weekRule;
    private String maxWeekKey;
    private String selectedLastDataWeekKey;
    private BigDecimal annualCumulativeMarketAvgPrice;
    private List<String> xAxis;
    private List<ChartSeriesDTO> series;
    private List<ProvincialSpotTrendWeekDTO> weeks;
}
