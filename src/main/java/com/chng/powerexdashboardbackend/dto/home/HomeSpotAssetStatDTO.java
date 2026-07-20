package com.chng.powerexdashboardbackend.dto.home;

import lombok.Data;

import java.time.LocalDate;

@Data
public class HomeSpotAssetStatDTO {
    private Integer genTypeId;
    private LocalDate minDate;
    private LocalDate maxDate;
    private Long recordCount;
}
