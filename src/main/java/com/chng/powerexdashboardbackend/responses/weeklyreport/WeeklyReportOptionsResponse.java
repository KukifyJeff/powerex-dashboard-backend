package com.chng.powerexdashboardbackend.responses.weeklyreport;

import lombok.Data;

import java.util.List;

@Data
public class WeeklyReportOptionsResponse {
    private String maxWeekKey;
    private List<String> weekOptions;
    private Integer maxRecentWeekCount;
    private Integer defaultRecentWeekCount;
}
