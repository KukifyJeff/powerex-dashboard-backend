package com.chng.powerexdashboardbackend.dto.ltledger;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class LTLedgerTrendDTO {
    private String period;
    private BigDecimal tradedPower;
    private BigDecimal weightedBenchmarkPrice;
    private BigDecimal chngTradedPrice;
}