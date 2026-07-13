package com.chng.powerexdashboardbackend.dto.weeklyreport;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class RegionalCompanyDailySpotPriceDTO {
    private LocalDate priceDate;
    private String companyName;
    private BigDecimal avgSpotPrice;
}
