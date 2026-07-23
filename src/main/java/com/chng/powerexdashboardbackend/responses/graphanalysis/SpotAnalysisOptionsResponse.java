package com.chng.powerexdashboardbackend.responses.graphanalysis;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class SpotAnalysisOptionsResponse {
    private String chartCode;
    private String chartName;
    private String weekRule;
    private String defaultFilterType;
    private String defaultTimeScale;
    private LocalDate minDate;
    private LocalDate maxDate;
    private LocalDate defaultStartDate;
    private LocalDate defaultEndDate;
    private List<GraphOptionItem> filterTypeOptions;
    private List<GraphOptionItem> companyOptions;
    private List<GraphOptionItem> regionOptions;
    private List<GraphOptionItem> timeScaleOptions;
}
