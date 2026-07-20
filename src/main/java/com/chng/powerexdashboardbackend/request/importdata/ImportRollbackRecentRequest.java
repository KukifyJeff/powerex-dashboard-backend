package com.chng.powerexdashboardbackend.request.importdata;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ImportRollbackRecentRequest {
    @Min(1)
    @Max(20)
    private Integer steps;

    @NotBlank
    private String adminPassword;

    private String reason;
}
