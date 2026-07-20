package com.chng.powerexdashboardbackend.responses.importdata;

import lombok.Data;

@Data
public class ImportDataVersionItem {
    private Long id;
    private String versionCode;
    private Long sourceJobId;
    private String status;
    private Integer longtermRowCount;
    private Integer spotRowCount;
    private String createdAt;
    private String activatedAt;
    private String rolledBackAt;
    private String remark;
}
