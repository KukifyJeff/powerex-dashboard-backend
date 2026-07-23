package com.chng.powerexdashboardbackend.responses.graphanalysis;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class SpotAnalysisPointDTO {
    private String periodKey;
    private String periodLabel;
    private LocalDate periodStartDate;
    private LocalDate periodEndDate;
    private BigDecimal minSpotPrice;
    private BigDecimal maxSpotPrice;
    private BigDecimal avgSpotPrice;
}
