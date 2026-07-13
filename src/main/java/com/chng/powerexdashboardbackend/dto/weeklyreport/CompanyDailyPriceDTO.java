package com.chng.powerexdashboardbackend.dto.weeklyreport;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class CompanyDailyPriceDTO {
    private LocalDate priceDate;
    private BigDecimal marketAvgPrice;
    private BigDecimal coalChngPrice;
    private BigDecimal windChngPrice;
    private BigDecimal solarChngPrice;
}
