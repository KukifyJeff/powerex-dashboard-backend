package com.chng.powerexdashboardbackend.responses.ltledger;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class LTLedgerResponse {
    private List<Map<String, Object>> table;
    private FilterOptions filters;
    private Meta meta;

    @Data
    public static class FilterOptions {
        private java.util.List<String> transactionTypes;
        private java.util.List<String> genTypes;
        private java.util.List<String> transactionPeriods;
        private java.util.List<String> contractStartDates;
        private java.util.List<String> contractEndDates;
        private java.util.List<String> greenPowerOptions;
    }

    @Data
    public static class Meta {
        private Integer companyCount;
        private Integer rowCount;
        private Boolean fullCompanyCoverage;
    }
}
