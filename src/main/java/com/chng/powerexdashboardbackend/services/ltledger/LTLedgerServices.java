package com.chng.powerexdashboardbackend.services.ltledger;

import com.chng.powerexdashboardbackend.dto.ltledger.LTLedgerDTO;
import com.chng.powerexdashboardbackend.dto.ltledger.LTLedgerSummaryDTO;
import com.chng.powerexdashboardbackend.dto.ltledger.LTLedgerTrendDTO;
import com.chng.powerexdashboardbackend.mapper.ltledger.LTLedgerMapper;
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
        resp.setRaw(rows);
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
        return mapper.getLedgerDetail(genTypeIds, transactionTypeIds, transactionPeriodIds, start, end, green);
    }

    public List<LTLedgerDTO> getLedger(LTLedgerQuery query) {
        return getDetail(query);
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

    public com.chng.powerexdashboardbackend.dto.ltledger.LTLedgerFilterOptionsDTO getFilterOptions() {
        com.chng.powerexdashboardbackend.dto.ltledger.LTLedgerFilterOptionsDTO opts = new com.chng.powerexdashboardbackend.dto.ltledger.LTLedgerFilterOptionsDTO();
        opts.setTransactionTypes(mapper.getTransactionTypes());
        opts.setGenTypes(mapper.getGenTypes());
        opts.setTransactionPeriods(mapper.getTransactionPeriods());
        opts.setGreenPowerOptions(mapper.getGreenPowerOptions());
        // mapper returns string dates; parse to LocalDate when present
        try {
            String min = mapper.getMinContractDate();
            String max = mapper.getMaxContractDate();
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

    private List<Integer> buildGreenList(Boolean isGreen) {
        if (isGreen == null) return null;
        List<Integer> l = new ArrayList<>();
        l.add(isGreen ? 1 : 0);
        return l;
    }
}
