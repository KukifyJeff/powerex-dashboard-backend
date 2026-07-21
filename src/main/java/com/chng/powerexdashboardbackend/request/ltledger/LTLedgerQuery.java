package com.chng.powerexdashboardbackend.request.ltledger;

import lombok.Data;
import java.util.List;

@Data
public class LTLedgerQuery {
    private Long companyId;
    private List<Integer> genTypeIds;
    private List<Integer> transactionTypeIds;
    private List<Integer> transactionPeriodIds;

    private String contractStartMonth;
    private String contractEndMonth;

    private Boolean isGreen;
}