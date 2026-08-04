package com.chng.powerexdashboardbackend.services.ltledger;

import com.chng.powerexdashboardbackend.dto.ltledger.LTLedgerDTO;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PivotServices {
    private static final long TOTAL_ROW_COMPANY_ID = -1L;
    private static final String TOTAL_ROW_COMPANY_NAME = "合计";

    public List<Map<String, Object>> buildPivot(List<LTLedgerDTO> rows) {
        return buildPivot(rows, false);
    }

    public List<Map<String, Object>> buildPivot(List<LTLedgerDTO> rows, boolean includeEnvPremium) {
        return buildPivot(rows, includeEnvPremium, true);
    }

    public List<Map<String, Object>> buildPivot(List<LTLedgerDTO> rows, boolean includeEnvPremium, boolean includeTotalRow) {
        if (rows == null || rows.isEmpty()) {
            return new ArrayList<>();
        }
        // Group by companyId to preserve id and name
        Map<Long, List<LTLedgerDTO>> byCompany = rows.stream().collect(Collectors.groupingBy(LTLedgerDTO::getCompanyId));
        List<Map<String, Object>> table = new ArrayList<>();
        for (Map.Entry<Long, List<LTLedgerDTO>> e : byCompany.entrySet()) {
            Long companyId = e.getKey();
            List<LTLedgerDTO> list = e.getValue();
            String companyName = list.stream().map(LTLedgerDTO::getCompanyName).filter(Objects::nonNull).findFirst().orElse(null);
            table.add(buildAggregateRow(companyId, companyName, list, includeEnvPremium));
        }
        // sort by companyId (nulls last)
        table.sort(Comparator.comparingLong(m -> {
            Object v = m.get("companyId");
            return v == null ? Long.MAX_VALUE : ((Number) v).longValue();
        }));
        if (includeTotalRow) {
            table.add(0, buildAggregateRow(TOTAL_ROW_COMPANY_ID, TOTAL_ROW_COMPANY_NAME, rows, includeEnvPremium));
        }
        return table;
    }

    private Map<String, Object> buildAggregateRow(Long companyId, String companyName, List<LTLedgerDTO> list, boolean includeEnvPremium) {
        BigDecimal totalAmount = list.stream()
                .map(LTLedgerDTO::getChngTransactionAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal numeratorChng = list.stream()
                .filter(d -> d.getChngTransactionAmount() != null && d.getChngTradedPrice() != null)
                .map(d -> d.getChngTransactionAmount().multiply(d.getChngTradedPrice()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        // For benchmark (base) price, use basePrice only (align with app.py logic), ignore marketAvgPrice/chngTradedPrice
        BigDecimal numeratorBase = list.stream()
                .filter(d -> d.getChngTransactionAmount() != null && d.getBasePrice() != null)
                .map(d -> d.getChngTransactionAmount().multiply(d.getBasePrice()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal numeratorEnvPremium = list.stream()
                .filter(d -> d.getChngTransactionAmount() != null && d.getEnvPremium() != null)
                .map(d -> d.getChngTransactionAmount().multiply(d.getEnvPremium()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal denom = list.stream()
                .map(LTLedgerDTO::getChngTransactionAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal envDenom = list.stream()
                .filter(d -> d.getChngTransactionAmount() != null && d.getEnvPremium() != null)
                .map(LTLedgerDTO::getChngTransactionAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal avgChngPrice = denom.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO : numeratorChng.divide(denom, 6, RoundingMode.HALF_UP);
        BigDecimal avgBenchmarkPrice = denom.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO : numeratorBase.divide(denom, 6, RoundingMode.HALF_UP);
        BigDecimal avgEnvPremium = envDenom.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO : numeratorEnvPremium.divide(envDenom, 6, RoundingMode.HALF_UP);

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("companyId", companyId);
        row.put("companyName", companyName);
        row.put("chngTransactionAmount", totalAmount);
        row.put("chngTradedPrice", avgChngPrice);
        row.put("weightedBenchmarkPrice", avgBenchmarkPrice);
        if (includeEnvPremium) {
            row.put("weightedEnvPremium", avgEnvPremium);
        }
        return row;
    }
}
