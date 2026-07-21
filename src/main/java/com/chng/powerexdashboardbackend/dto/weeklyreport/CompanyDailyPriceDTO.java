package com.chng.powerexdashboardbackend.dto.weeklyreport;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class CompanyDailyPriceDTO {
    private LocalDate priceDate;
    private BigDecimal marketAvgPrice;
    private BigDecimal marketPriceSum;
    private Long marketCompanyCount;
    private BigDecimal coalChngPrice;
    private BigDecimal coalGenAmount;
    private BigDecimal windChngPrice;
    private BigDecimal windGenAmount;
    private BigDecimal solarChngPrice;
    private BigDecimal solarGenAmount;
}
