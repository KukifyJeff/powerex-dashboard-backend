package com.chng.powerexdashboardbackend.responses.importdata;

import lombok.Data;

@Data
public class ImportDataActionResponse {
    private boolean success;
    private String message;
    private Long jobId;
    private Long versionId;
    private String versionCode;
}
