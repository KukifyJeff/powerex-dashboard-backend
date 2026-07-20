package com.chng.powerexdashboardbackend.responses.importdata;

import lombok.Data;

@Data
public class ImportDataFileItem {
    private String fileName;
    private String dataType;
    private String status;
    private Integer totalRows;
    private Integer normalizedRows;
    private Integer skippedRows;
    private Integer errorCount;
    private String errorMessage;
}
