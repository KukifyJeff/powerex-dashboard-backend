package com.chng.powerexdashboardbackend.request.importdata;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ImportRollbackRequest {
    @NotNull
    private Long versionId;

    @NotBlank
    private String adminPassword;

    private String reason;
}
