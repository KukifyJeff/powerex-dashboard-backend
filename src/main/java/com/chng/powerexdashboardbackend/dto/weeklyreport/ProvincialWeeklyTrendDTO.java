package com.chng.powerexdashboardbackend.dto.weeklyreport;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class ProvincialWeeklyTrendDTO {
    private LocalDate weekStartDate;
    private LocalDate weekEndDate;
    private BigDecimal marketAvgPrice;
    private BigDecimal contractAvgPrice;
    private BigDecimal chngAvgPrice;
}
