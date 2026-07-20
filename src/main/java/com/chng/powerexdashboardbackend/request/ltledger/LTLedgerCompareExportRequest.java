package com.chng.powerexdashboardbackend.request.ltledger;

import lombok.Data;

import java.util.List;

@Data
public class LTLedgerCompareExportRequest {
    // legacy/explicit compare payload
    private List<CompareItem> items;

    // frontend compare payload:
    // { query, compareDimension, compareGroups }
    private LTLedgerQuery query;
    private String compareDimension;
    private List<List<Integer>> compareGroups;

    @Data
    public static class CompareItem {
        private String label;
        private LTLedgerQuery query;
    }
}
