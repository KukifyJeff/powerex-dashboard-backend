package com.chng.powerexdashboardbackend.controller;

import com.chng.powerexdashboardbackend.responses.graphanalysis.SpotAnalysisOptionsResponse;
import com.chng.powerexdashboardbackend.responses.graphanalysis.SpotAnalysisTrendResponse;
import com.chng.powerexdashboardbackend.responses.graphanalysis.LongtermAnalysisOptionsResponse;
import com.chng.powerexdashboardbackend.responses.graphanalysis.LongtermAnalysisTrendResponse;
import com.chng.powerexdashboardbackend.services.GraphAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/graph-analysis")
@RequiredArgsConstructor
public class GraphAnalysisController {

    private final GraphAnalysisService graphAnalysisService;

    @GetMapping("/spot-analysis/options")
    public SpotAnalysisOptionsResponse spotAnalysisOptions() {
        return graphAnalysisService.getSpotAnalysisOptions();
    }

    @GetMapping("/spot-analysis/trend")
    public SpotAnalysisTrendResponse spotAnalysisTrend(
            @RequestParam(defaultValue = "company") String filterType,
            @RequestParam(required = false) List<Integer> filterIds,
            @RequestParam(defaultValue = "week") String timeScale,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return graphAnalysisService.getSpotAnalysisTrend(filterType, filterIds, timeScale, startDate, endDate);
    }

    @GetMapping("/longterm-analysis/options")
    public LongtermAnalysisOptionsResponse longtermAnalysisOptions() {
        return graphAnalysisService.getLongtermAnalysisOptions();
    }

    @GetMapping("/longterm-analysis/trend")
    public LongtermAnalysisTrendResponse longtermAnalysisTrend(
            @RequestParam(defaultValue = "company") String filterType,
            @RequestParam(required = false) List<Integer> filterIds,
            @RequestParam(required = false) String month) {
        return graphAnalysisService.getLongtermAnalysisTrend(filterType, filterIds, month);
    }
}
