package com.chng.powerexdashboardbackend.dto.weeklyreport;

import lombok.Data;

import java.util.List;

@Data
public class LongtermAmountPriceTrendResponseDTO {
    private String unitAmount;
    private String unitPrice;
    private String maxWeekKey;
    private String selectedLastDataWeekKey;
    private List<String> xAxis;
    private List<ChartSeriesDTO> series;
    private List<LongtermAmountPriceTrendDTO> periods;
}
