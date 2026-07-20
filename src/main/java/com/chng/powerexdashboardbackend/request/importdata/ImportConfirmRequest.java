package com.chng.powerexdashboardbackend.request.importdata;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ImportConfirmRequest {
    @NotNull
    private Long jobId;

    @NotBlank
    private String adminPassword;

    private String remark;
}
