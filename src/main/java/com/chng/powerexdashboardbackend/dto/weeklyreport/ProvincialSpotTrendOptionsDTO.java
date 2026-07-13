package com.chng.powerexdashboardbackend.dto.weeklyreport;

import lombok.Data;

import java.util.List;

@Data
public class ProvincialSpotTrendOptionsDTO {
    private String maxWeekKey;
    private List<String> weekOptions;
}
