package com.chng.powerexdashboardbackend.dto.weeklyreport;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class ProvincialSpotCompanyDailyDTO {
    private LocalDate priceDate;
    private Long companyId;
    private String companyName;
    private Integer regionId;
    private BigDecimal coalSpotAvgPrice;
    private BigDecimal windSpotAvgPrice;
}
