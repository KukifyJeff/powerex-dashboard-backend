package com.chng.powerexdashboardbackend.dto.ltledger;

import lombok.Data;
import java.util.List;
import java.time.LocalDate;

@Data
public class LTLedgerFilterOptionsDTO {
    private List<Integer> transactionTypeIds;
    private List<Integer> genTypeIds;
    private List<Integer> transactionPeriodIds;
    private List<Integer> greenPowerOptions;
    private LocalDate minContractDate;
    private LocalDate maxContractDate;
}