package com.chng.powerexdashboardbackend.responses.home;

import lombok.Data;

@Data
public class HomeRecentImportItem {
    private String id;
    private String type;
    private String time;
    private Integer added;
    private Integer updated;
    private String status;
}
