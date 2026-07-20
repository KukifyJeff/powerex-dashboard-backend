package com.chng.powerexdashboardbackend.responses.home;

import lombok.Data;

import java.util.List;

@Data
public class HomeOverviewData {
    private String pageTitle;
    private String pageSubtitle;
    private List<HomeTopMetricItem> topMetrics;
    private List<HomeDataAssetItem> dataAssets;
    private HomeDataStatus dataStatus;
    private List<HomeRecentImportItem> recentImports;
    private String updatedAt;
}
