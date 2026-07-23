package com.chng.powerexdashboardbackend.responses.graphanalysis;

import com.chng.powerexdashboardbackend.dto.weeklyreport.ChartSeriesDTO;
import com.chng.powerexdashboardbackend.dto.weeklyreport.LongtermAmountPriceTrendDTO;
import lombok.Data;

import java.util.List;

@Data
public class LongtermAnalysisTrendResponse {
    private String chartCode;
    private String chartName;
    private String unitAmount;
    private String unitPrice;
    private String filterType;
    private List<Integer> filterIds;
    private String selectedMonth;
    private List<String> xAxis;
    private List<ChartSeriesDTO> series;
    private List<LongtermAmountPriceTrendDTO> periods;
}
