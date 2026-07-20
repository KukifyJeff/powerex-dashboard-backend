package com.chng.powerexdashboardbackend.responses.weeklyreport;

import com.chng.powerexdashboardbackend.dto.weeklyreport.ChartSeriesDTO;
import com.chng.powerexdashboardbackend.dto.weeklyreport.LongtermAmountPriceTrendDTO;
import lombok.Data;

import java.util.List;

@Data
public class LongtermAmountPriceTrendResponse {
    private String unitAmount;
    private String unitPrice;
    private String maxWeekKey;
    private String selectedLastDataWeekKey;
    private List<String> xAxis;
    private List<ChartSeriesDTO> series;
    private List<LongtermAmountPriceTrendDTO> periods;
}
