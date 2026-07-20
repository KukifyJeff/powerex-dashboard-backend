package com.chng.powerexdashboardbackend.controller;

import com.chng.powerexdashboardbackend.responses.home.HomeOverviewResponse;
import com.chng.powerexdashboardbackend.services.HomeOverviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/home")
@RequiredArgsConstructor
public class HomeController {

    private final HomeOverviewService homeOverviewService;

    @GetMapping("/overview")
    public HomeOverviewResponse getOverview(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate) {
        return homeOverviewService.getHomeOverview(asOfDate);
    }
}
