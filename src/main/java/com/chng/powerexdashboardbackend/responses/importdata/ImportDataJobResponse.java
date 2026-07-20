package com.chng.powerexdashboardbackend.responses.importdata;

import lombok.Data;

import java.util.List;

@Data
public class ImportDataJobResponse {
    private Long jobId;
    private String status;
    private Integer uploadedFileCount;
    private Integer longtermRowCount;
    private Integer spotRowCount;
    private Integer failedFileCount;
    private String errorMessage;
    private String createdAt;
    private String normalizedAt;
    private String confirmedAt;
    private List<ImportDataFileItem> files;
}
