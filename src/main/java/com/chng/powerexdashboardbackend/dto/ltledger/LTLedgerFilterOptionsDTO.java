package com.chng.powerexdashboardbackend.dto.ltledger;

import lombok.Data;
import java.util.List;
import java.time.LocalDate;

@Data
public class LTLedgerFilterOptionsDTO {
    private List<String> transactionTypes;
    private List<String> genTypes;
    private List<String> transactionPeriods;
    private List<String> greenPowerOptions;
    private LocalDate minContractDate;
    private LocalDate maxContractDate;
}