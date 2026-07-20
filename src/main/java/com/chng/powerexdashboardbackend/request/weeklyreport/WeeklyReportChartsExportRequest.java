package com.chng.powerexdashboardbackend.request.weeklyreport;

import lombok.Data;

import java.util.List;

@Data
public class WeeklyReportChartsExportRequest {
    private String lastDataWeekKey;
    private List<WeeklyReportChartExportItem> charts;
}
