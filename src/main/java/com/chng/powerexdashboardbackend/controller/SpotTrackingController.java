package com.chng.powerexdashboardbackend.controller;

import com.chng.powerexdashboardbackend.responses.spottracking.*;
import com.chng.powerexdashboardbackend.services.SpotTrackingServices;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/spot-tracking")
@RequiredArgsConstructor
public class SpotTrackingController {

    private final SpotTrackingServices service;

    // ========================= TAB1 =========================
    @GetMapping("/spot")
    public List<SpotDataResponse> spot(
            @RequestParam Integer genTypeId,
            @RequestParam LocalDate startDate,
            @RequestParam LocalDate endDate) {
        return service.getSpotSummary(genTypeId, startDate, endDate);
    }

    // ========================= TAB2 =========================
    @GetMapping("/yearly")
    public List<LongtermYearlyResponse> yearly(
            @RequestParam Integer genTypeId,
            @RequestParam LocalDate startDate,
            @RequestParam LocalDate endDate) {
        return service.getYearlySummary(genTypeId, startDate, endDate);
    }

    // ========================= TAB3 =========================
    @GetMapping("/monthly")
    public List<LongtermMonthlyResponse> monthly(
            @RequestParam Integer genTypeId,
            @RequestParam LocalDate startDate,
            @RequestParam LocalDate endDate) {
        return service.getMonthlySummary(genTypeId, startDate, endDate);
    }

    // ========================= TAB4 =========================
    @GetMapping("/summary")
    public List<SpotTrackingSummaryResponse> summary(
            @RequestParam Integer genTypeId,
            @RequestParam LocalDate startDate,
            @RequestParam LocalDate endDate) {
        return service.getFinalSummary(genTypeId, startDate, endDate);
    }
    @GetMapping("/contract-years")
    public List<Integer> getContractYears() {
        return service.getContractYears();
    }
}