package com.chng.powerexdashboardbackend.dto.ltledger;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class LTLedgerSummaryDTO {
    private BigDecimal totalTradedPower;
    private BigDecimal weightedBenchmarkPrice;
    private BigDecimal chngTradedPrice;
    private Integer companyCount;
}