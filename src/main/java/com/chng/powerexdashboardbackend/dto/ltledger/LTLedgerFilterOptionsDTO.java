package com.chng.powerexdashboardbackend.dto.ltledger;

import lombok.Data;
import java.util.List;

@Data
public class LTLedgerFilterOptionsDTO {
    private List<Integer> transactionTypeIds;
    private List<Integer> genTypeIds;
    private List<Integer> transactionPeriodIds;
    private List<Integer> greenPowerOptions;
    private String minContractMonth;
    private String maxContractMonth;
}