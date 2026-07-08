package com.chng.powerexdashboardbackend.services.ltledger;

import com.chng.powerexdashboardbackend.dto.ltledger.LTLedgerDTO;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PivotServices {

    public List<Map<String, Object>> buildPivot(List<LTLedgerDTO> rows) {
        // Simple pivot: group by company name, sum totalAmount and weightedPrice (average weighted)
        Map<String, List<LTLedgerDTO>> byCompany = rows.stream().collect(Collectors.groupingBy(LTLedgerDTO::getCompanyName));
        List<Map<String, Object>> table = new ArrayList<>();
        for (Map.Entry<String, List<LTLedgerDTO>> e : byCompany.entrySet()) {
            String company = e.getKey();
            List<LTLedgerDTO> list = e.getValue();
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
            row.put("companyName", company);
            row.put("totalAmount", totalAmount);
            row.put("weightedPrice", avgPrice);
            table.add(row);
        }
        // sort by companyName
        table.sort(Comparator.comparing(m -> (String) m.get("companyName")));
        return table;
    }
}
