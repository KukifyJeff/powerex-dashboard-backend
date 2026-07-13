package com.chng.powerexdashboardbackend.dto.weeklyreport;

import lombok.Data;

import java.util.List;

@Data
public class RegionalSpotTrendCompanyDTO {
    private String companyName;
    private List<RegionalSpotTrendWeekDTO> weeks;
}
