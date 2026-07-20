package com.chng.powerexdashboardbackend.request.ltledger;

import lombok.Data;
import java.time.LocalDate;
import java.util.List;

@Data
public class LTLedgerQuery {
    private Long companyId;
    private List<Integer> genTypeIds;
    private List<Integer> transactionTypeIds;
    private List<Integer> transactionPeriodIds;

    private LocalDate contractStartDate;
    private LocalDate contractEndDate;

    private Boolean isGreen;
}