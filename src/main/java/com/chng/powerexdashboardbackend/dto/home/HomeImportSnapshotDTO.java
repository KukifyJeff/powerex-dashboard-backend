package com.chng.powerexdashboardbackend.dto.home;

import lombok.Data;

import java.time.LocalDate;

@Data
public class HomeImportSnapshotDTO {
    private String type;
    private LocalDate importDate;
    private Long added;
}
