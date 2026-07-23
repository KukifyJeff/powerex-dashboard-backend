package com.chng.powerexdashboardbackend.dto.graphanalysis;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class SpotAnalysisDailyPriceDTO {
    private LocalDate priceDate;
    private BigDecimal avgSpotPrice;
}
