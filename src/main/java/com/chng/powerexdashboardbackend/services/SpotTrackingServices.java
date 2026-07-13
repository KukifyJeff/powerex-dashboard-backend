package com.chng.powerexdashboardbackend.services;

import com.chng.powerexdashboardbackend.utils.MetricCalculationUtil;

import com.chng.powerexdashboardbackend.dto.spottracking.*;
import com.chng.powerexdashboardbackend.mapper.spottracking.LongtermMapper;
import com.chng.powerexdashboardbackend.mapper.spottracking.SpotMapper;
import com.chng.powerexdashboardbackend.responses.spottracking.SpotDataResponse;
import com.chng.powerexdashboardbackend.responses.spottracking.LongtermYearlyResponse;
import com.chng.powerexdashboardbackend.responses.spottracking.LongtermMonthlyResponse;
import com.chng.powerexdashboardbackend.responses.spottracking.SpotTrackingSummaryResponse;
import com.chng.powerexdashboardbackend.utils.CompanyDefaultOrderSortUtil;
import com.chng.powerexdashboardbackend.utils.CsvExportUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SpotTrackingServices {

    private final SpotMapper spotMapper;
    private final LongtermMapper longtermMapper;

    // ================= TAB1 =================
    public List<SpotDataResponse> getSpotSummary(Integer genTypeId,
                                                 LocalDate startDate,
                                                 LocalDate endDate) {
        return getSpotSummary(genTypeId, startDate, endDate, false);
    }

    public List<SpotDataResponse> getSpotSummary(Integer genTypeId,
                                                 LocalDate startDate,
                                                 LocalDate endDate,
                                                 boolean includeTotal) {

        List<SpotDataDTO> list =
                spotMapper.selectCompanySpotSummary(genTypeId, startDate, endDate);

        if (list == null) return List.of();

        List<SpotDataResponse> responseList = new ArrayList<>();

        for (SpotDataDTO s : list) {
            BigDecimal genAmountRaw = nvl(s.getGenAmount());
            BigDecimal longtermAmountRaw = nvl(s.getLongtermAmount());

            BigDecimal genAmount = genAmountRaw.divide(new BigDecimal("100000"), 6, RoundingMode.HALF_UP);
            BigDecimal longtermAmount = longtermAmountRaw.divide(new BigDecimal("100000"), 6, RoundingMode.HALF_UP);

            BigDecimal longtermRevenueSum = nvl(s.getLongtermRevenueSum());
            BigDecimal chngRevenueSum = nvl(s.getChngRevenueSum());

            BigDecimal holdingRate = BigDecimal.ZERO;
            if (genAmountRaw.compareTo(BigDecimal.ZERO) > 0) {
                holdingRate = longtermAmountRaw.divide(genAmountRaw, 6, RoundingMode.HALF_UP);
            }

            BigDecimal spotAvgPrice = nvl(s.getSpotAvgPrice());

            BigDecimal longtermAvgPrice = BigDecimal.ZERO;
            if (longtermAmountRaw.compareTo(BigDecimal.ZERO) > 0) {
                longtermAvgPrice = longtermRevenueSum.divide(longtermAmountRaw, 6, RoundingMode.HALF_UP);
            }

            BigDecimal chngAvgPrice = BigDecimal.ZERO;
            if (genAmountRaw.compareTo(BigDecimal.ZERO) > 0) {
                chngAvgPrice = chngRevenueSum.divide(genAmountRaw, 6, RoundingMode.HALF_UP);
            }

            SpotDataResponse resp = new SpotDataResponse();
            resp.setCompanyId(s.getCompanyId() == null ? null : s.getCompanyId().intValue());
            resp.setCompanyName(s.getCompanyName());
            resp.setGenAmount(genAmount);
            resp.setLongtermAmount(longtermAmount);
            resp.setHoldingRate(holdingRate);
            resp.setSpotAvgPrice(spotAvgPrice);
            resp.setLongtermAvgPrice(longtermAvgPrice);
            resp.setChngAvgPrice(chngAvgPrice);
            responseList.add(resp);
        }
        CompanyDefaultOrderSortUtil.sortByCompanyId(responseList, SpotDataResponse::getCompanyId);
        if (includeTotal && !responseList.isEmpty()) {
            responseList.add(buildSpotTotalRow(responseList));
        }
        return responseList;
    }

    // ================= TAB2 =================
    public List<LongtermYearlyResponse> getYearlySummary(Integer genTypeId,
                                                         LocalDate startDate,
                                                         LocalDate endDate) {
        List<LongtermYearlyDTO> list =
                longtermMapper.selectYearlySummary(genTypeId, startDate, endDate);
        if (list == null) return List.of();
        List<LongtermYearlyResponse> respList = new ArrayList<>();
        for (LongtermYearlyDTO dto : list) {
            LongtermYearlyResponse resp = new LongtermYearlyResponse();
            resp.setCompanyId(dto.getCompanyId() == null ? null : dto.getCompanyId().intValue());
            resp.setCompanyName(dto.getCompanyName());
            resp.setTransactionAmount(
                    nvl(dto.getTransactionAmount())
            );
            resp.setTransactionPrice(nvl(dto.getTransactionPrice()));
            respList.add(resp);
        }
        CompanyDefaultOrderSortUtil.sortByCompanyId(respList, LongtermYearlyResponse::getCompanyId);
        return respList;
    }

    // ================= TAB3 =================
    public List<LongtermMonthlyResponse> getMonthlySummary(Integer genTypeId,
                                                          LocalDate startDate,
                                                          LocalDate endDate) {
        List<LongtermMonthlyDTO> list =
                longtermMapper.selectMonthlySummary(genTypeId, startDate, endDate);
        if (list == null) return List.of();
        List<LongtermMonthlyResponse> respList = new ArrayList<>();
        for (LongtermMonthlyDTO dto : list) {
            LongtermMonthlyResponse resp = new LongtermMonthlyResponse();
            resp.setCompanyId(dto.getCompanyId() == null ? null : dto.getCompanyId().intValue());
            resp.setCompanyName(dto.getCompanyName());
            resp.setTransactionAmount(
                    nvl(dto.getTransactionAmount())
            );
            resp.setTransactionPrice(nvl(dto.getTransactionPrice()));
            respList.add(resp);
        }
        CompanyDefaultOrderSortUtil.sortByCompanyId(respList, LongtermMonthlyResponse::getCompanyId);
        return respList;
    }

    // ================= TAB4 =================
    public List<SpotTrackingSummaryResponse> getFinalSummary(Integer genTypeId,
                                                            LocalDate startDate,
                                                            LocalDate endDate) {
        return getFinalSummary(genTypeId, startDate, endDate, false);
    }

    public List<SpotTrackingSummaryResponse> getFinalSummary(Integer genTypeId,
                                                             LocalDate startDate,
                                                             LocalDate endDate,
                                                             boolean includeTotal) {

        List<SpotDataDTO> spotList = spotMapper.selectCompanySpotSummary(genTypeId, startDate, endDate);
        List<LongtermYearlyResponse> yearlyList = getYearlySummary(genTypeId, startDate, endDate);
        List<LongtermMonthlyResponse> monthlyList = getMonthlySummary(genTypeId, startDate, endDate);

        java.util.Map<Integer, LongtermYearlyResponse> yearlyMap = yearlyList.stream()
                .collect(java.util.stream.Collectors.toMap(LongtermYearlyResponse::getCompanyId, v -> v, (a, b) -> a));

        java.util.Map<Integer, LongtermMonthlyResponse> monthlyMap = monthlyList.stream()
                .collect(java.util.stream.Collectors.toMap(LongtermMonthlyResponse::getCompanyId, v -> v, (a, b) -> a));

        List<SpotTrackingSummaryResponse> result = new ArrayList<>();

        BigDecimal sumGen = BigDecimal.ZERO;
        BigDecimal sumLongterm = BigDecimal.ZERO;
        BigDecimal sumYearlyAmount = BigDecimal.ZERO;
        BigDecimal sumYearlyPriceNum = BigDecimal.ZERO;
        BigDecimal sumMonthlyAmount = BigDecimal.ZERO;
        BigDecimal sumMonthlyPriceNum = BigDecimal.ZERO;
        BigDecimal sumLongtermAvgPriceNum = BigDecimal.ZERO;
        BigDecimal sumSpotAvgPrice = BigDecimal.ZERO;
        int spotAvgCount = 0;
        BigDecimal sumChngAvgPriceNum = BigDecimal.ZERO;
        BigDecimal sumPriceDiffNum = BigDecimal.ZERO;
        BigDecimal sumUnitCapacityIncomeNum = BigDecimal.ZERO;

        for (SpotDataDTO spot : spotList) {

            BigDecimal genAmountRaw = nvl(spot.getGenAmount());
            BigDecimal longtermAmountRaw = nvl(spot.getLongtermAmount());

            BigDecimal genAmount = genAmountRaw.divide(new BigDecimal("100000"), 6, RoundingMode.HALF_UP);
            BigDecimal longtermAmount = longtermAmountRaw.divide(new BigDecimal("100000"), 6, RoundingMode.HALF_UP);

            BigDecimal longtermRevenueSum = nvl(spot.getLongtermRevenueSum());
            BigDecimal chngRevenueSum = nvl(spot.getChngRevenueSum());

            BigDecimal holdingRate = BigDecimal.ZERO;
            if (genAmountRaw.compareTo(BigDecimal.ZERO) > 0) {
                holdingRate = longtermAmountRaw.divide(genAmountRaw, 6, RoundingMode.HALF_UP);
            }

            BigDecimal spotAvgPrice = nvl(spot.getSpotAvgPrice());

            BigDecimal longtermAvgPrice = BigDecimal.ZERO;
            if (longtermAmountRaw.compareTo(BigDecimal.ZERO) > 0) {
                longtermAvgPrice = longtermRevenueSum.divide(longtermAmountRaw, 6, RoundingMode.HALF_UP);
            }

            BigDecimal chngAvgPrice = BigDecimal.ZERO;
            if (genAmountRaw.compareTo(BigDecimal.ZERO) > 0) {
                chngAvgPrice = chngRevenueSum.divide(genAmountRaw, 6, RoundingMode.HALF_UP);
            }

            SpotTrackingSummaryResponse row = new SpotTrackingSummaryResponse();

            row.setCompanyId(spot.getCompanyId() == null ? null : spot.getCompanyId().intValue());
            row.setCompanyName(spot.getCompanyName());

            LongtermYearlyResponse yearly = yearlyMap.get(spot.getCompanyId() == null ? null : spot.getCompanyId().intValue());
            LongtermMonthlyResponse monthly = monthlyMap.get(spot.getCompanyId() == null ? null : spot.getCompanyId().intValue());

            BigDecimal yearlyAmount = yearly == null ? BigDecimal.ZERO : nvl(yearly.getTransactionAmount());
            BigDecimal yearlyPrice = yearly == null ? BigDecimal.ZERO : nvl(yearly.getTransactionPrice());
            BigDecimal monthlyAmount = monthly == null ? BigDecimal.ZERO : nvl(monthly.getTransactionAmount());
            BigDecimal monthlyPrice = monthly == null ? BigDecimal.ZERO : nvl(monthly.getTransactionPrice());

            BigDecimal yearlyHoldingRate = MetricCalculationUtil.ratio(yearlyAmount, genAmount);

            row.setYearlyHoldingRate(yearlyHoldingRate);
            row.setTotalHoldingRate(holdingRate);
            row.setYearlyPrice(yearlyPrice);
            row.setMonthlyPrice(monthlyPrice);
            row.setLongtermAvgPrice(longtermAvgPrice);
            row.setSpotAvgPrice(spotAvgPrice);
            row.setChngAvgPrice(chngAvgPrice);

            BigDecimal basePrice = yearlyPrice.compareTo(BigDecimal.ZERO) > 0 ? yearlyPrice : monthlyPrice;
            row.setPriceDiff(chngAvgPrice.subtract(basePrice));

            if (genTypeId != null && genTypeId == 1) {
                BigDecimal capacity = nvl(spot.getCapacity());
                if (capacity.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal income = chngAvgPrice
                            .multiply(genAmountRaw)
                            .multiply(BigDecimal.TEN)
                            .divide(capacity, 6, RoundingMode.HALF_UP)
                            .divide(new BigDecimal("100000"), 6, RoundingMode.HALF_UP);
                    row.setUnitCapacityIncome(income);
                } else {
                    row.setUnitCapacityIncome(BigDecimal.ZERO);
                }
            } else {
                row.setUnitCapacityIncome(null);
            }

            result.add(row);

            sumGen = sumGen.add(genAmount);
            sumLongterm = sumLongterm.add(longtermAmount);
            sumYearlyAmount = sumYearlyAmount.add(yearlyAmount);
            sumYearlyPriceNum = sumYearlyPriceNum.add(yearlyAmount.multiply(yearlyPrice));
            sumMonthlyAmount = sumMonthlyAmount.add(monthlyAmount);
            sumMonthlyPriceNum = sumMonthlyPriceNum.add(monthlyAmount.multiply(monthlyPrice));
            sumLongtermAvgPriceNum = sumLongtermAvgPriceNum.add(longtermAmount.multiply(longtermAvgPrice));
            if (genAmount.compareTo(BigDecimal.ZERO) > 0) {
                sumSpotAvgPrice = sumSpotAvgPrice.add(spotAvgPrice);
                spotAvgCount++;
            }
            sumChngAvgPriceNum = sumChngAvgPriceNum.add(genAmount.multiply(chngAvgPrice));
            sumPriceDiffNum = sumPriceDiffNum.add(genAmount.multiply(row.getPriceDiff()));
            if (row.getUnitCapacityIncome() != null) {
                sumUnitCapacityIncomeNum = sumUnitCapacityIncomeNum.add(genAmount.multiply(row.getUnitCapacityIncome()));
            }
        }
        CompanyDefaultOrderSortUtil.sortByCompanyId(result, SpotTrackingSummaryResponse::getCompanyId);
        if (includeTotal && !result.isEmpty()) {
            SpotTrackingSummaryResponse total = new SpotTrackingSummaryResponse();
            total.setCompanyId(Integer.MAX_VALUE);
            total.setCompanyName("合计");
            total.setYearlyHoldingRate(ratio(sumYearlyAmount, sumGen));
            total.setTotalHoldingRate(ratio(sumLongterm, sumGen));
            total.setYearlyPrice(ratio(sumYearlyPriceNum, sumYearlyAmount));
            total.setMonthlyPrice(ratio(sumMonthlyPriceNum, sumMonthlyAmount));
            total.setLongtermAvgPrice(ratio(sumLongtermAvgPriceNum, sumLongterm));
            total.setSpotAvgPrice(spotAvgCount == 0 ? BigDecimal.ZERO :
                    sumSpotAvgPrice.divide(BigDecimal.valueOf(spotAvgCount), 6, RoundingMode.HALF_UP));
            total.setChngAvgPrice(ratio(sumChngAvgPriceNum, sumGen));
            total.setPriceDiff(ratio(sumPriceDiffNum, sumGen));
            if (genTypeId != null && genTypeId == 1) {
                total.setUnitCapacityIncome(ratio(sumUnitCapacityIncomeNum, sumGen));
            } else {
                total.setUnitCapacityIncome(null);
            }
            result.add(total);
        }
        return result;
    }

    // ================= helper =================
    private BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private BigDecimal ratio(BigDecimal numerator, BigDecimal denominator) {
        if (denominator == null || denominator.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return nvl(numerator).divide(denominator, 6, RoundingMode.HALF_UP);
    }

    private SpotDataResponse buildSpotTotalRow(List<SpotDataResponse> rows) {
        BigDecimal sumGen = BigDecimal.ZERO;
        BigDecimal sumLongterm = BigDecimal.ZERO;
        BigDecimal longtermAvgPriceNum = BigDecimal.ZERO;
        BigDecimal chngAvgPriceNum = BigDecimal.ZERO;
        BigDecimal spotAvgPriceSum = BigDecimal.ZERO;
        int spotAvgCount = 0;

        for (SpotDataResponse r : rows) {
            BigDecimal gen = nvl(r.getGenAmount());
            BigDecimal longterm = nvl(r.getLongtermAmount());
            sumGen = sumGen.add(gen);
            sumLongterm = sumLongterm.add(longterm);
            longtermAvgPriceNum = longtermAvgPriceNum.add(longterm.multiply(nvl(r.getLongtermAvgPrice())));
            chngAvgPriceNum = chngAvgPriceNum.add(gen.multiply(nvl(r.getChngAvgPrice())));
            if (gen.compareTo(BigDecimal.ZERO) > 0 && r.getSpotAvgPrice() != null) {
                spotAvgPriceSum = spotAvgPriceSum.add(r.getSpotAvgPrice());
                spotAvgCount++;
            }
        }

        SpotDataResponse total = new SpotDataResponse();
        total.setCompanyId(Integer.MAX_VALUE);
        total.setCompanyName("合计");
        total.setGenAmount(sumGen);
        total.setLongtermAmount(sumLongterm);
        total.setLongtermAvgPrice(ratio(longtermAvgPriceNum, sumLongterm));
        total.setHoldingRate(ratio(sumLongterm, sumGen));
        total.setSpotAvgPrice(spotAvgCount == 0 ? BigDecimal.ZERO :
                spotAvgPriceSum.divide(BigDecimal.valueOf(spotAvgCount), 6, RoundingMode.HALF_UP));
        total.setChngAvgPrice(ratio(chngAvgPriceNum, sumGen));
        return total;
    }
    public List<Integer> getContractYears() {
        return spotMapper.selectContractYears();
    }

    public byte[] exportCsv(String table, Integer genTypeId, LocalDate startDate, LocalDate endDate) {
        String target = table == null ? "spot" : table.trim().toLowerCase();
        return switch (target) {
            case "spot" -> exportSpotCsv(genTypeId, startDate, endDate);
            case "yearly" -> exportYearlyCsv(genTypeId, startDate, endDate);
            case "monthly" -> exportMonthlyCsv(genTypeId, startDate, endDate);
            case "summary" -> exportSummaryCsv(genTypeId, startDate, endDate);
            default -> throw new IllegalArgumentException("Unsupported export table: " + table);
        };
    }

    private byte[] exportSpotCsv(Integer genTypeId, LocalDate startDate, LocalDate endDate) {
        List<SpotDataResponse> rows = getSpotSummary(genTypeId, startDate, endDate);
        List<String> headers = List.of(
                "companyId", "companyName", "genAmount", "longtermAmount", "longtermAvgPrice",
                "holdingRate", "spotAvgPrice", "chngAvgPrice"
        );
        List<List<?>> data = new ArrayList<>();
        for (SpotDataResponse r : rows) {
            data.add(Arrays.asList(
                    r.getCompanyId(), r.getCompanyName(), r.getGenAmount(), r.getLongtermAmount(),
                    r.getLongtermAvgPrice(), r.getHoldingRate(), r.getSpotAvgPrice(), r.getChngAvgPrice()
            ));
        }
        return CsvExportUtil.toCsvBytes(headers, data);
    }

    private byte[] exportYearlyCsv(Integer genTypeId, LocalDate startDate, LocalDate endDate) {
        List<LongtermYearlyResponse> rows = getYearlySummary(genTypeId, startDate, endDate);
        List<String> headers = List.of("companyId", "companyName", "transactionAmount", "transactionPrice");
        List<List<?>> data = new ArrayList<>();
        for (LongtermYearlyResponse r : rows) {
            data.add(Arrays.asList(r.getCompanyId(), r.getCompanyName(), r.getTransactionAmount(), r.getTransactionPrice()));
        }
        return CsvExportUtil.toCsvBytes(headers, data);
    }

    private byte[] exportMonthlyCsv(Integer genTypeId, LocalDate startDate, LocalDate endDate) {
        List<LongtermMonthlyResponse> rows = getMonthlySummary(genTypeId, startDate, endDate);
        List<String> headers = List.of("companyId", "companyName", "transactionAmount", "transactionPrice");
        List<List<?>> data = new ArrayList<>();
        for (LongtermMonthlyResponse r : rows) {
            data.add(Arrays.asList(r.getCompanyId(), r.getCompanyName(), r.getTransactionAmount(), r.getTransactionPrice()));
        }
        return CsvExportUtil.toCsvBytes(headers, data);
    }

    private byte[] exportSummaryCsv(Integer genTypeId, LocalDate startDate, LocalDate endDate) {
        List<SpotTrackingSummaryResponse> rows = getFinalSummary(genTypeId, startDate, endDate);
        List<String> headers = List.of(
                "companyId", "companyName", "yearlyHoldingRate", "totalHoldingRate", "yearlyPrice", "monthlyPrice",
                "longtermAvgPrice", "spotAvgPrice", "chngAvgPrice", "priceDiff", "unitCapacityIncome"
        );
        List<List<?>> data = new ArrayList<>();
        for (SpotTrackingSummaryResponse r : rows) {
            data.add(Arrays.asList(
                    r.getCompanyId(), r.getCompanyName(), r.getYearlyHoldingRate(), r.getTotalHoldingRate(),
                    r.getYearlyPrice(), r.getMonthlyPrice(), r.getLongtermAvgPrice(), r.getSpotAvgPrice(),
                    r.getChngAvgPrice(), r.getPriceDiff(), r.getUnitCapacityIncome()
            ));
        }
        return CsvExportUtil.toCsvBytes(headers, data);
    }
}