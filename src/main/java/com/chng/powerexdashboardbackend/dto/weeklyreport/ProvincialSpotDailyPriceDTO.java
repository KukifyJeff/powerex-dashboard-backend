package com.chng.powerexdashboardbackend.dto.weeklyreport;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class ProvincialSpotDailyPriceDTO {
    private LocalDate priceDate;
    private BigDecimal marketAvgPrice;
}
