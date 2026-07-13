package com.chng.powerexdashboardbackend.controller;

import com.chng.powerexdashboardbackend.responses.spottracking.*;
import com.chng.powerexdashboardbackend.services.SpotTrackingServices;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeParseException;
import java.time.LocalDate;
import java.time.YearMonth;
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
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "false") boolean includeTotal) {
        DateRange range = resolveDateRange(startDate, endDate);
        return service.getSpotSummary(genTypeId, range.startDate(), range.endDate(), includeTotal);
    }

    // ========================= TAB2 =========================
    @GetMapping("/yearly")
    public List<LongtermYearlyResponse> yearly(
            @RequestParam Integer genTypeId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        DateRange range = resolveDateRange(startDate, endDate);
        return service.getYearlySummary(genTypeId, range.startDate(), range.endDate());
    }

    // ========================= TAB3 =========================
    @GetMapping("/monthly")
    public List<LongtermMonthlyResponse> monthly(
            @RequestParam Integer genTypeId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        DateRange range = resolveDateRange(startDate, endDate);
        return service.getMonthlySummary(genTypeId, range.startDate(), range.endDate());
    }

    // ========================= TAB4 =========================
    @GetMapping("/summary")
    public List<SpotTrackingSummaryResponse> summary(
            @RequestParam Integer genTypeId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "false") boolean includeTotal) {
        DateRange range = resolveDateRange(startDate, endDate);
        return service.getFinalSummary(genTypeId, range.startDate(), range.endDate(), includeTotal);
    }
    @GetMapping("/contract-years")
    public List<Integer> getContractYears() {
        return service.getContractYears();
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestParam Integer genTypeId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "spot") String table) {
        DateRange range = resolveDateRange(startDate, endDate);
        byte[] csv;
        try {
            csv = service.exportCsv(table, genTypeId, range.startDate(), range.endDate());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
        String fileName = "spot-tracking-" + table + ".csv";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .body(csv);
    }

    private DateRange resolveDateRange(String startDate,
                                       String endDate) {
        LocalDate resolvedStart = parseFlexibleDate(startDate, false);
        LocalDate resolvedEnd = parseFlexibleDate(endDate, true);

        if (resolvedStart == null) {
            resolvedStart = LocalDate.of(1900, 1, 1);
        }
        if (resolvedEnd == null) {
            resolvedEnd = LocalDate.of(2999, 12, 31);
        }
        if (resolvedStart.isAfter(resolvedEnd)) {
            LocalDate tmp = resolvedStart;
            resolvedStart = resolvedEnd;
            resolvedEnd = tmp;
        }
        return new DateRange(resolvedStart, resolvedEnd);
    }

    private LocalDate parseFlexibleDate(String value, boolean endOfPeriod) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String v = value.trim();
        try {
            YearMonth ym = YearMonth.parse(v);
            return endOfPeriod ? ym.atEndOfMonth() : ym.atDay(1);
        } catch (DateTimeParseException ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Date must be in YYYY-MM format: " + v
            );
        }
    }

    private record DateRange(LocalDate startDate, LocalDate endDate) {}
}