package com.chng.powerexdashboardbackend.dto.weeklyreport;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class LongtermAmountPriceTrendDTO {
    private String period;
    private BigDecimal coalAmount;
    private BigDecimal windAmount;
    private BigDecimal solarAmount;
    private BigDecimal coalPrice;
    private BigDecimal windPrice;
    private BigDecimal solarPrice;
}
