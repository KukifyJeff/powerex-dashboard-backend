package com.chng.powerexdashboardbackend.responses.importdata;

import lombok.Data;

@Data
public class ImportDataUploadResponse {
    private boolean success;
    private String message;
    private ImportDataJobResponse data;
}
