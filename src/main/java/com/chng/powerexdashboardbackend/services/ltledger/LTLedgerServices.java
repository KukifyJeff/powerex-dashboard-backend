package com.chng.powerexdashboardbackend.services.ltledger;

import com.chng.powerexdashboardbackend.dto.ltledger.LTLedgerDTO;
import com.chng.powerexdashboardbackend.dto.ltledger.LTLedgerSummaryDTO;
import com.chng.powerexdashboardbackend.dto.ltledger.LTLedgerTrendDTO;
import com.chng.powerexdashboardbackend.mapper.ltledger.LTLedgerMapper;
import com.chng.powerexdashboardbackend.request.LTLedgerOptionsQuery;
import com.chng.powerexdashboardbackend.request.LTLedgerQuery;
import com.chng.powerexdashboardbackend.responses.ltledger.LTLedgerResponse;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
public class LTLedgerServices {

    private final LTLedgerMapper mapper;
    private final PivotServices pivotServices;

    public LTLedgerServices(LTLedgerMapper mapper, PivotServices pivotServices) {
        this.mapper = mapper;
        this.pivotServices = pivotServices;
    }

    public LTLedgerResponse getPivot(LTLedgerQuery query) {
        List<Integer> genTypeIds = resolveGenTypeIds(query);
        List<Integer> transactionTypeIds = resolveTransactionTypeIds(query);
        List<Integer> transactionPeriodIds = resolveTransactionPeriodIds(query);
        String start = query.getContractStartDate() == null ? null : query.getContractStartDate().toString();
        String end = query.getContractEndDate() == null ? null : query.getContractEndDate().toString();
        List<Integer> green = buildGreenList(query.getIsGreen());

        List<LTLedgerDTO> rows = mapper.getLedgerPivot(genTypeIds, transactionTypeIds, transactionPeriodIds, start, end, green);
        List<Map<String, Object>> table = pivotServices.buildPivot(rows);

        LTLedgerResponse resp = new LTLedgerResponse();
        resp.setTable(table);
        LTLedgerResponse.Meta meta = new LTLedgerResponse.Meta();
        meta.setCompanyCount(table.size());
        meta.setRowCount(rows.size());
        meta.setFullCompanyCoverage(false);
        resp.setMeta(meta);
        return resp;
    }

    public List<LTLedgerDTO> getDetail(LTLedgerQuery query) {
        List<Integer> genTypeIds = resolveGenTypeIds(query);
        List<Integer> transactionTypeIds = resolveTransactionTypeIds(query);
        List<Integer> transactionPeriodIds = resolveTransactionPeriodIds(query);
        String start = query.getContractStartDate() == null ? null : query.getContractStartDate().toString();
        String end = query.getContractEndDate() == null ? null : query.getContractEndDate().toString();
        List<Integer> green = buildGreenList(query.getIsGreen());
        return mapper.getLedgerDetail(query.getCompanyId(), genTypeIds, transactionTypeIds, transactionPeriodIds, start, end, green);
    }

    public LTLedgerSummaryDTO getSummary(LTLedgerQuery query) {
        List<Integer> genTypeIds = resolveGenTypeIds(query);
        List<Integer> transactionTypeIds = resolveTransactionTypeIds(query);
        List<Integer> transactionPeriodIds = resolveTransactionPeriodIds(query);
        String start = query.getContractStartDate() == null ? null : query.getContractStartDate().toString();
        String end = query.getContractEndDate() == null ? null : query.getContractEndDate().toString();
        List<Integer> green = buildGreenList(query.getIsGreen());
        return mapper.getLedgerSummary(genTypeIds, transactionTypeIds, transactionPeriodIds, start, end, green);
    }

    public List<LTLedgerTrendDTO> getTrend(LTLedgerQuery query) {
        List<Integer> genTypeIds = resolveGenTypeIds(query);
        List<Integer> transactionTypeIds = resolveTransactionTypeIds(query);
        List<Integer> transactionPeriodIds = resolveTransactionPeriodIds(query);
        String start = query.getContractStartDate() == null ? null : query.getContractStartDate().toString();
        String end = query.getContractEndDate() == null ? null : query.getContractEndDate().toString();
        List<Integer> green = buildGreenList(query.getIsGreen());
        return mapper.getLedgerTrend(genTypeIds, transactionTypeIds, transactionPeriodIds, start, end, green);
    }

    public com.chng.powerexdashboardbackend.dto.ltledger.LTLedgerFilterOptionsDTO getFilterOptions(LTLedgerOptionsQuery query) {
        List<Integer> genTypeIds = query == null ? null : resolveGenTypeIds(query);
        List<Integer> transactionTypeIds = query == null ? null : resolveTransactionTypeIds(query);
        List<Integer> transactionPeriodIds = query == null ? null : resolveTransactionPeriodIds(query);
        String start = query == null || query.getContractStartDate() == null ? null : query.getContractStartDate().toString();
        String end = query == null || query.getContractEndDate() == null ? null : query.getContractEndDate().toString();
        List<Integer> green = query == null ? null : buildGreenList(query.getIsGreen());

        com.chng.powerexdashboardbackend.dto.ltledger.LTLedgerFilterOptionsDTO opts = new com.chng.powerexdashboardbackend.dto.ltledger.LTLedgerFilterOptionsDTO();
        opts.setTransactionTypeIds(mapper.getTransactionTypes(genTypeIds, transactionTypeIds, transactionPeriodIds, start, end, green));
        opts.setGenTypeIds(mapper.getGenTypes(genTypeIds, transactionTypeIds, transactionPeriodIds, start, end, green));
        opts.setTransactionPeriodIds(mapper.getTransactionPeriods(genTypeIds, transactionTypeIds, transactionPeriodIds, start, end, green));
        opts.setGreenPowerOptions(mapper.getGreenPowerOptions(genTypeIds, transactionTypeIds, transactionPeriodIds, start, end, green));
        // mapper returns string dates; parse to LocalDate when present
        try {
            // keep date bounds globally fixed, not narrowed by current filter selections
            String min = mapper.getMinContractDate(null, null, null, null, null, null);
            String max = mapper.getMaxContractDate(null, null, null, null, null, null);
            if (min != null) opts.setMinContractDate(java.time.LocalDate.parse(min));
            if (max != null) opts.setMaxContractDate(java.time.LocalDate.parse(max));
        } catch (Exception ex) {
            // ignore parse errors and leave nulls
        }
        return opts;
    }

    // helpers: resolve names -> ids when names present

    private List<Integer> resolveGenTypeIds(LTLedgerQuery q) {
        return q.getGenTypeIds();
    }

    private List<Integer> resolveTransactionTypeIds(LTLedgerQuery q) {
        return q.getTransactionTypeIds();
    }

    private List<Integer> resolveTransactionPeriodIds(LTLedgerQuery q) {
        return q.getTransactionPeriodIds();
    }

    private List<Integer> resolveGenTypeIds(LTLedgerOptionsQuery q) {
        return q.getGenTypeIds();
    }

    private List<Integer> resolveTransactionTypeIds(LTLedgerOptionsQuery q) {
        return q.getTransactionTypeIds();
    }

    private List<Integer> resolveTransactionPeriodIds(LTLedgerOptionsQuery q) {
        return q.getTransactionPeriodIds();
    }

    private List<Integer> buildGreenList(Boolean isGreen) {
        if (isGreen == null) return null;
        List<Integer> l = new ArrayList<>();
        l.add(isGreen ? 1 : 0);
        return l;
    }
}
