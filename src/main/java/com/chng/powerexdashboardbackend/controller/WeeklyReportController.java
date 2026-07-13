package com.chng.powerexdashboardbackend.controller;

import com.chng.powerexdashboardbackend.dto.weeklyreport.CompanyPriceTrendResponseDTO;
import com.chng.powerexdashboardbackend.dto.weeklyreport.LongtermAmountPriceTrendResponseDTO;
import com.chng.powerexdashboardbackend.dto.weeklyreport.ProvincialSpotTrendOptionsDTO;
import com.chng.powerexdashboardbackend.dto.weeklyreport.ProvincialSpotTrendResponseDTO;
import com.chng.powerexdashboardbackend.dto.weeklyreport.RegionalSpotTrendResponseDTO;
import com.chng.powerexdashboardbackend.services.WeeklyReportServices;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/weekly-report")
@RequiredArgsConstructor
public class WeeklyReportController {

    private final WeeklyReportServices weeklyReportServices;

    @GetMapping("/provincial-spot-trend/options")
    public ProvincialSpotTrendOptionsDTO provincialSpotTrendOptions() {
        return weeklyReportServices.getProvincialSpotTrendOptions();
    }

    @GetMapping("/provincial-spot-trend")
    public ProvincialSpotTrendResponseDTO provincialSpotTrend(
            @RequestParam(required = false) String lastDataWeekKey) {
        return weeklyReportServices.getProvincialSpotTrend(lastDataWeekKey);
    }

    @GetMapping("/company-price-trend")
    public CompanyPriceTrendResponseDTO companyPriceTrend(
            @RequestParam(required = false) String lastDataWeekKey) {
        return weeklyReportServices.getCompanyPriceTrend(lastDataWeekKey);
    }

    @GetMapping("/longterm-amount-price-trend")
    public LongtermAmountPriceTrendResponseDTO longtermAmountPriceTrend(
            @RequestParam(required = false) String lastDataWeekKey) {
        return weeklyReportServices.getLongtermAmountPriceTrend(lastDataWeekKey);
    }

    @GetMapping("/regional-spot-trend")
    public RegionalSpotTrendResponseDTO regionalSpotTrend(
            @RequestParam Integer regionId,
            @RequestParam(required = false) String lastDataWeekKey) {
        return weeklyReportServices.getRegionalSpotTrend(regionId, lastDataWeekKey);
    }
}
