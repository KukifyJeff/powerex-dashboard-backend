package com.chng.powerexdashboardbackend.dto.graphanalysis;

import lombok.Data;

import java.time.LocalDate;

@Data
public class SpotDateRangeDTO {
    private LocalDate minDate;
    private LocalDate maxDate;
}
