package com.chng.powerexdashboardbackend.controller;

import com.chng.powerexdashboardbackend.responses.weeklyreport.CompanyPriceTrendResponse;
import com.chng.powerexdashboardbackend.responses.weeklyreport.LongtermAmountPriceTrendResponse;
import com.chng.powerexdashboardbackend.responses.weeklyreport.WeeklyReportOptionsResponse;
import com.chng.powerexdashboardbackend.responses.weeklyreport.ProvincialSpotTrendResponse;
import com.chng.powerexdashboardbackend.responses.weeklyreport.RegionalSpotTrendResponse;
import com.chng.powerexdashboardbackend.request.weeklyreport.WeeklyReportChartsExportRequest;
import com.chng.powerexdashboardbackend.services.WeeklyReportServices;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/weekly-report")
@RequiredArgsConstructor
public class WeeklyReportController {

    private final WeeklyReportServices weeklyReportServices;

    @GetMapping("/options")
    public WeeklyReportOptionsResponse weeklyReportOptionsResponse() {
        return weeklyReportServices.getWeeklyReportOptionsResponse();
    }

    @GetMapping("/provincial-spot-trend")
    public ProvincialSpotTrendResponse provincialSpotTrend(
            @RequestParam(required = false) String lastDataWeekKey,
            @RequestParam(required = false) Integer recentWeekCount) {
        return weeklyReportServices.getProvincialSpotTrend(lastDataWeekKey, recentWeekCount);
    }

    @GetMapping("/company-price-trend")
    public CompanyPriceTrendResponse companyPriceTrend(
            @RequestParam(required = false) String lastDataWeekKey,
            @RequestParam(required = false) Integer recentWeekCount) {
        return weeklyReportServices.getCompanyPriceTrend(lastDataWeekKey, recentWeekCount);
    }

    @GetMapping("/longterm-amount-price-trend")
    public LongtermAmountPriceTrendResponse longtermAmountPriceTrend(
            @RequestParam(required = false) String lastDataWeekKey) {
        return weeklyReportServices.getLongtermAmountPriceTrend(lastDataWeekKey);
    }

    @GetMapping("/regional-spot-trend")
    public RegionalSpotTrendResponse regionalSpotTrend(
            @RequestParam Integer regionId,
            @RequestParam(required = false) String lastDataWeekKey,
            @RequestParam(required = false) Integer recentWeekCount) {
        return weeklyReportServices.getRegionalSpotTrend(regionId, lastDataWeekKey, recentWeekCount);
    }

    @PostMapping("/export-charts")
    public ResponseEntity<byte[]> exportCharts(@RequestBody WeeklyReportChartsExportRequest request) {
        WeeklyReportServices.WeeklyReportChartsZipResult result =
                weeklyReportServices.exportWeeklyReportChartsZip(request);
        String encoded = URLEncoder.encode(result.fileName(), StandardCharsets.UTF_8).replace("+", "%20");
        String disposition = "attachment; filename=\"weekly-report.zip\"; filename*=UTF-8''" + encoded;
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .body(result.content());
    }
}
