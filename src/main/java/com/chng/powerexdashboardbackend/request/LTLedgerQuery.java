package com.chng.powerexdashboardbackend.request;

import lombok.Data;
import java.time.LocalDate;
import java.util.List;

@Data
public class LTLedgerQuery {
    private List<Integer> genTypeIds;
    private List<Integer> transactionTypeIds;
    private List<Integer> transactionPeriodIds;

    private LocalDate contractStartDate;
    private LocalDate contractEndDate;

    private Boolean isGreen;
}