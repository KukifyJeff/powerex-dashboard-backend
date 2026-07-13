package com.chng.powerexdashboardbackend.dto.weeklyreport;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class RegionalSpotTrendWeekDTO {
    private LocalDate weekStartDate;
    private LocalDate weekEndDate;
    private BigDecimal avgSpotPrice;
}
