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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.ArrayList;

@Service
@RequiredArgsConstructor
public class SpotTrackingServices {

    private final SpotMapper spotMapper;
    private final LongtermMapper longtermMapper;

    // ================= TAB1 =================
    public List<SpotDataResponse> getSpotSummary(Integer genTypeId,
                                                 LocalDate startDate,
                                                 LocalDate endDate) {

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

        List<SpotDataDTO> spotList = spotMapper.selectCompanySpotSummary(genTypeId, startDate, endDate);
        List<LongtermYearlyResponse> yearlyList = getYearlySummary(genTypeId, startDate, endDate);
        List<LongtermMonthlyResponse> monthlyList = getMonthlySummary(genTypeId, startDate, endDate);

        java.util.Map<Integer, LongtermYearlyResponse> yearlyMap = yearlyList.stream()
                .collect(java.util.stream.Collectors.toMap(LongtermYearlyResponse::getCompanyId, v -> v, (a, b) -> a));

        java.util.Map<Integer, LongtermMonthlyResponse> monthlyMap = monthlyList.stream()
                .collect(java.util.stream.Collectors.toMap(LongtermMonthlyResponse::getCompanyId, v -> v, (a, b) -> a));

        List<SpotTrackingSummaryResponse> result = new ArrayList<>();

        for (SpotDataDTO spot : spotList) {

            BigDecimal genAmountRaw = nvl(spot.getGenAmount());
            BigDecimal longtermAmountRaw = nvl(spot.getLongtermAmount());

            BigDecimal genAmount = genAmountRaw.divide(new BigDecimal("100000"), 6, RoundingMode.HALF_UP);

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
        }
        CompanyDefaultOrderSortUtil.sortByCompanyId(result, SpotTrackingSummaryResponse::getCompanyId);
        return result;
    }

    // ================= helper =================
    private BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
    public List<Integer> getContractYears() {
        return spotMapper.selectContractYears();
    }
}