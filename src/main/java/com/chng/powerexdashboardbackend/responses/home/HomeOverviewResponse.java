package com.chng.powerexdashboardbackend.responses.home;

import lombok.Data;

@Data
public class HomeOverviewResponse {
    private boolean success;
    private String message;
    private HomeOverviewData data;
}
