package com.chng.powerexdashboardbackend.services.ltledger;

import com.chng.powerexdashboardbackend.dto.ltledger.LTLedgerDTO;
import com.chng.powerexdashboardbackend.dto.ltledger.LTLedgerSummaryDTO;
import com.chng.powerexdashboardbackend.dto.ltledger.LTLedgerTrendDTO;
import com.chng.powerexdashboardbackend.mapper.ltledger.LTLedgerMapper;
import com.chng.powerexdashboardbackend.request.ltledger.LTLedgerCompareExportRequest;
import com.chng.powerexdashboardbackend.request.ltledger.LTLedgerOptionsQuery;
import com.chng.powerexdashboardbackend.request.ltledger.LTLedgerQuery;
import com.chng.powerexdashboardbackend.responses.ltledger.LTLedgerResponse;
import com.chng.powerexdashboardbackend.utils.CsvExportUtil;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class LTLedgerServices {
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");

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
        ContractRange contractRange = resolveContractRange(query);
        List<Integer> green = buildGreenList(query.getIsGreen());
        boolean includeEnvPremium = Boolean.TRUE.equals(query.getIsGreen());

        List<LTLedgerDTO> rows = mapper.getLedgerPivot(genTypeIds, transactionTypeIds, transactionPeriodIds, contractRange.start(), contractRange.end(), green);
        List<Map<String, Object>> table = pivotServices.buildPivot(rows, includeEnvPremium);

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
        ContractRange contractRange = resolveContractRange(query);
        List<Integer> green = buildGreenList(query.getIsGreen());
        return mapper.getLedgerDetail(query.getCompanyId(), genTypeIds, transactionTypeIds, transactionPeriodIds, contractRange.start(), contractRange.end(), green);
    }

    public LTLedgerSummaryDTO getSummary(LTLedgerQuery query) {
        List<Integer> genTypeIds = resolveGenTypeIds(query);
        List<Integer> transactionTypeIds = resolveTransactionTypeIds(query);
        List<Integer> transactionPeriodIds = resolveTransactionPeriodIds(query);
        ContractRange contractRange = resolveContractRange(query);
        List<Integer> green = buildGreenList(query.getIsGreen());
        return mapper.getLedgerSummary(genTypeIds, transactionTypeIds, transactionPeriodIds, contractRange.start(), contractRange.end(), green);
    }

    public List<LTLedgerTrendDTO> getTrend(LTLedgerQuery query) {
        List<Integer> genTypeIds = resolveGenTypeIds(query);
        List<Integer> transactionTypeIds = resolveTransactionTypeIds(query);
        List<Integer> transactionPeriodIds = resolveTransactionPeriodIds(query);
        ContractRange contractRange = resolveContractRange(query);
        List<Integer> green = buildGreenList(query.getIsGreen());
        return mapper.getLedgerTrend(genTypeIds, transactionTypeIds, transactionPeriodIds, contractRange.start(), contractRange.end(), green);
    }

    public com.chng.powerexdashboardbackend.dto.ltledger.LTLedgerFilterOptionsDTO getFilterOptions(LTLedgerOptionsQuery query) {
        List<Integer> genTypeIds = query == null ? null : resolveGenTypeIds(query);
        List<Integer> transactionTypeIds = query == null ? null : resolveTransactionTypeIds(query);
        List<Integer> transactionPeriodIds = query == null ? null : resolveTransactionPeriodIds(query);
        ContractRange contractRange = resolveContractRange(query);
        List<Integer> green = query == null ? null : buildGreenList(query.getIsGreen());

        com.chng.powerexdashboardbackend.dto.ltledger.LTLedgerFilterOptionsDTO opts = new com.chng.powerexdashboardbackend.dto.ltledger.LTLedgerFilterOptionsDTO();
        opts.setTransactionTypeIds(mapper.getTransactionTypes(genTypeIds, transactionTypeIds, transactionPeriodIds, contractRange.start(), contractRange.end(), green));
        opts.setGenTypeIds(mapper.getGenTypes(genTypeIds, transactionTypeIds, transactionPeriodIds, contractRange.start(), contractRange.end(), green));
        opts.setTransactionPeriodIds(mapper.getTransactionPeriods(genTypeIds, transactionTypeIds, transactionPeriodIds, contractRange.start(), contractRange.end(), green));
        opts.setGreenPowerOptions(mapper.getGreenPowerOptions(genTypeIds, transactionTypeIds, transactionPeriodIds, contractRange.start(), contractRange.end(), green));
        // mapper returns string dates; parse to LocalDate when present
        try {
            // keep date bounds globally fixed, not narrowed by current filter selections
            String min = mapper.getMinContractDate(null, null, null, null, null, null);
            String max = mapper.getMaxContractDate(null, null, null, null, null, null);
            if (min != null) {
                opts.setMinContractMonth(parseYearMonthFromDate(min).format(MONTH_FORMATTER));
            }
            if (max != null) {
                opts.setMaxContractMonth(parseYearMonthFromDate(max).format(MONTH_FORMATTER));
            }
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

    public byte[] exportPivotCsv(LTLedgerQuery query) {
        LTLedgerResponse resp = getPivot(query == null ? new LTLedgerQuery() : query);
        boolean includeEnvPremium = resp.getTable() != null
                && !resp.getTable().isEmpty()
                && resp.getTable().get(0).containsKey("weightedEnvPremium");
        List<String> headers = new ArrayList<>(List.of("companyId", "companyName", "chngTransactionAmount", "chngTradedPrice", "weightedBenchmarkPrice"));
        if (includeEnvPremium) {
            headers.add("weightedEnvPremium");
        }
        List<List<?>> rows = new ArrayList<>();
        for (Map<String, Object> row : resp.getTable()) {
            List<Object> line = new ArrayList<>(Arrays.asList(
                    row.get("companyId"),
                    row.get("companyName"),
                    row.get("chngTransactionAmount"),
                    row.get("chngTradedPrice"),
                    row.get("weightedBenchmarkPrice")
            ));
            if (includeEnvPremium) {
                line.add(row.get("weightedEnvPremium"));
            }
            rows.add(line);
        }
        return CsvExportUtil.toCsvBytes(headers, rows);
    }

    public byte[] exportComparePivotCsv(LTLedgerCompareExportRequest request) {
        List<LTLedgerCompareExportRequest.CompareItem> compareItems = resolveCompareItems(request);

        Map<Long, LinkedHashMap<String, Object>> merged = new LinkedHashMap<>();
        List<String> headers = new ArrayList<>();
        headers.add("companyId");
        headers.add("companyName");

        for (int i = 0; i < compareItems.size(); i++) {
            LTLedgerCompareExportRequest.CompareItem item = compareItems.get(i);
            String label = normalizeLabel(item == null ? null : item.getLabel(), i + 1);

            headers.add(label + "_chngTransactionAmount");
            headers.add(label + "_chngTradedPrice");
            headers.add(label + "_weightedBenchmarkPrice");

            LTLedgerQuery q = item == null || item.getQuery() == null ? new LTLedgerQuery() : item.getQuery();
            List<Map<String, Object>> table = getPivot(q).getTable();
            boolean includeEnvPremium = table != null
                    && !table.isEmpty()
                    && table.get(0).containsKey("weightedEnvPremium");
            if (includeEnvPremium) {
                headers.add(label + "_weightedEnvPremium");
            }

            for (Map<String, Object> row : table) {
                Object idObj = row.get("companyId");
                if (!(idObj instanceof Number n)) {
                    continue;
                }
                Long companyId = n.longValue();
                LinkedHashMap<String, Object> mergedRow = merged.computeIfAbsent(companyId, k -> {
                    LinkedHashMap<String, Object> m = new LinkedHashMap<>();
                    m.put("companyId", companyId);
                    m.put("companyName", row.get("companyName"));
                    return m;
                });
                mergedRow.put(label + "_chngTransactionAmount", row.get("chngTransactionAmount"));
                mergedRow.put(label + "_chngTradedPrice", row.get("chngTradedPrice"));
                mergedRow.put(label + "_weightedBenchmarkPrice", row.get("weightedBenchmarkPrice"));
                if (includeEnvPremium) {
                    mergedRow.put(label + "_weightedEnvPremium", row.get("weightedEnvPremium"));
                }
            }
        }

        List<LinkedHashMap<String, Object>> sortedRows = new ArrayList<>(merged.values());
        sortedRows.sort(Comparator.comparingLong(r -> ((Number) r.get("companyId")).longValue()));

        List<List<?>> data = new ArrayList<>();
        for (LinkedHashMap<String, Object> row : sortedRows) {
            List<Object> line = new ArrayList<>();
            for (String h : headers) {
                line.add(row.get(h));
            }
            data.add(line);
        }
        return CsvExportUtil.toCsvBytes(headers, data);
    }

    private List<LTLedgerCompareExportRequest.CompareItem> resolveCompareItems(LTLedgerCompareExportRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("compare request is required");
        }
        if (request.getItems() != null && !request.getItems().isEmpty()) {
            return request.getItems();
        }

        if (request.getQuery() == null
                || request.getCompareDimension() == null
                || request.getCompareDimension().isBlank()
                || request.getCompareGroups() == null
                || request.getCompareGroups().isEmpty()) {
            throw new IllegalArgumentException("items is required for compare export, or use {query, compareDimension, compareGroups}");
        }

        String dimension = request.getCompareDimension().trim();
        List<LTLedgerCompareExportRequest.CompareItem> result = new ArrayList<>();
        for (List<Integer> group : request.getCompareGroups()) {
            LTLedgerCompareExportRequest.CompareItem item = new LTLedgerCompareExportRequest.CompareItem();
            LTLedgerQuery q = cloneQuery(request.getQuery());
            applyCompareGroup(q, dimension, group);
            item.setQuery(q);
            item.setLabel(group == null || group.isEmpty() ? dimension : dimension + "_" + group.toString().replace(" ", ""));
            result.add(item);
        }
        return result;
    }

    private LTLedgerQuery cloneQuery(LTLedgerQuery source) {
        LTLedgerQuery copy = new LTLedgerQuery();
        copy.setCompanyId(source.getCompanyId());
        copy.setGenTypeIds(source.getGenTypeIds() == null ? null : new ArrayList<>(source.getGenTypeIds()));
        copy.setTransactionTypeIds(source.getTransactionTypeIds() == null ? null : new ArrayList<>(source.getTransactionTypeIds()));
        copy.setTransactionPeriodIds(source.getTransactionPeriodIds() == null ? null : new ArrayList<>(source.getTransactionPeriodIds()));
        copy.setContractStartMonth(source.getContractStartMonth());
        copy.setContractEndMonth(source.getContractEndMonth());
        copy.setIsGreen(source.getIsGreen());
        return copy;
    }

    private ContractRange resolveContractRange(LTLedgerQuery query) {
        if (query == null) {
            return new ContractRange(null, null);
        }
        return resolveContractRange(
                query.getContractStartMonth(),
                query.getContractEndMonth()
        );
    }

    private ContractRange resolveContractRange(LTLedgerOptionsQuery query) {
        if (query == null) {
            return new ContractRange(null, null);
        }
        return resolveContractRange(
                query.getContractStartMonth(),
                query.getContractEndMonth()
        );
    }

    private ContractRange resolveContractRange(String contractStartMonth,
                                               String contractEndMonth) {
        YearMonth start = null;
        YearMonth end = null;
        if (contractStartMonth != null && !contractStartMonth.isBlank()) {
            start = parseYearMonth(contractStartMonth);
        }
        if (contractEndMonth != null && !contractEndMonth.isBlank()) {
            end = parseYearMonth(contractEndMonth);
        }
        return new ContractRange(
                start == null ? null : start.atDay(1).toString(),
                end == null ? null : end.atEndOfMonth().toString()
        );
    }

    private YearMonth parseYearMonth(String raw) {
        try {
            return YearMonth.parse(raw.trim(), MONTH_FORMATTER);
        } catch (DateTimeParseException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid contract month, expected YYYY-MM: " + raw);
        }
    }

    private YearMonth parseYearMonthFromDate(String rawDate) {
        String trimmed = rawDate == null ? "" : rawDate.trim();
        if (trimmed.length() < 7) {
            throw new DateTimeParseException("Invalid date for month extraction", trimmed, 0);
        }
        return parseYearMonth(trimmed.substring(0, 7));
    }

    private void applyCompareGroup(LTLedgerQuery query, String dimension, List<Integer> group) {
        List<Integer> values = group == null ? List.of() : group;
        switch (dimension) {
            case "genTypeIds" -> query.setGenTypeIds(values.isEmpty() ? null : new ArrayList<>(values));
            case "transactionTypeIds" -> query.setTransactionTypeIds(values.isEmpty() ? null : new ArrayList<>(values));
            case "transactionPeriodIds" -> query.setTransactionPeriodIds(values.isEmpty() ? null : new ArrayList<>(values));
            case "isGreen" -> {
                if (values.isEmpty()) {
                    query.setIsGreen(null);
                } else {
                    query.setIsGreen(values.get(0) != null && values.get(0) != 0);
                }
            }
            case "companyId" -> {
                if (values.isEmpty() || values.get(0) == null) {
                    query.setCompanyId(null);
                } else {
                    query.setCompanyId(values.get(0).longValue());
                }
            }
            default -> throw new IllegalArgumentException("Unsupported compareDimension: " + dimension);
        }
    }

    private String normalizeLabel(String label, int index) {
        if (label == null || label.isBlank()) {
            return "compare" + index;
        }
        return label.trim().replaceAll("\\s+", "_");
    }

    private record ContractRange(String start, String end) {
    }
}
