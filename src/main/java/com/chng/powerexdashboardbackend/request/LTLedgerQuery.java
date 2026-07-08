package com.chng.powerexdashboardbackend.request;

import lombok.Data;
import java.time.LocalDate;
import java.util.List;

@Data
public class LTLedgerQuery {
    private List<Integer> genTypeIds;
    private List<String> genTypeNames;

    private List<Integer> transactionTypeIds;
    private List<String> transactionTypes;

    private List<Integer> transactionPeriodIds;
    private List<String> transactionPeriodNames;

    private List<String> contractPeriods;

    private Boolean isGreen;

    private LocalDate transactionDate;

    private LocalDate contractStartDate;

    private LocalDate contractEndDate;
}