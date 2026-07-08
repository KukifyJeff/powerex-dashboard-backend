package com.chng.powerexdashboardbackend.services.ltledger;

import com.chng.powerexdashboardbackend.dto.ltledger.LTLedgerDTO;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PivotServices {

    public List<Map<String, Object>> buildPivot(List<LTLedgerDTO> rows) {
        // Group by companyId to preserve id and name
        Map<Long, List<LTLedgerDTO>> byCompany = rows.stream().collect(Collectors.groupingBy(LTLedgerDTO::getCompanyId));
        List<Map<String, Object>> table = new ArrayList<>();
        for (Map.Entry<Long, List<LTLedgerDTO>> e : byCompany.entrySet()) {
            Long companyId = e.getKey();
            List<LTLedgerDTO> list = e.getValue();
            String companyName = list.stream().map(LTLedgerDTO::getCompanyName).filter(Objects::nonNull).findFirst().orElse(null);

            // aggregate using BigDecimal fields directly
            BigDecimal totalAmount = list.stream()
                    .map(LTLedgerDTO::getChngTransactionAmount)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal numerator = list.stream()
                    .filter(d -> d.getChngTransactionAmount() != null && d.getChngTradedPrice() != null)
                    .map(d -> d.getChngTransactionAmount().multiply(d.getChngTradedPrice()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal denom = list.stream()
                    .map(LTLedgerDTO::getChngTransactionAmount)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal avgPrice = denom.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO : numerator.divide(denom, 6, BigDecimal.ROUND_HALF_UP);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("companyId", companyId);
            row.put("companyName", companyName);
            row.put("chngTransactionAmount", totalAmount);
            row.put("chngTradedPrice", avgPrice);
            table.add(row);
        }
        // sort by companyId (nulls last)
        table.sort(Comparator.comparingLong(m -> {
            Object v = m.get("companyId");
            return v == null ? Long.MAX_VALUE : ((Number) v).longValue();
        }));
        return table;
    }
}
