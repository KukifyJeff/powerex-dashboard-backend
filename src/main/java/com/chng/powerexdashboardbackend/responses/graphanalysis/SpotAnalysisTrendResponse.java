package com.chng.powerexdashboardbackend.responses.graphanalysis;

import com.chng.powerexdashboardbackend.dto.weeklyreport.ChartSeriesDTO;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class SpotAnalysisTrendResponse {
    private String chartCode;
    private String chartName;
    private String unit;
    private String weekRule;
    private String filterType;
    private List<Integer> filterIds;
    private String timeScale;
    private LocalDate startDate;
    private LocalDate endDate;
    private List<String> xAxis;
    private List<ChartSeriesDTO> series;
    private List<SpotAnalysisPointDTO> points;
}
