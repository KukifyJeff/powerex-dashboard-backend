package com.chng.powerexdashboardbackend.responses.graphanalysis;

import lombok.Data;

import java.util.List;

@Data
public class LongtermAnalysisOptionsResponse {
    private String chartCode;
    private String chartName;
    private String defaultFilterType;
    private String defaultMonth;
    private List<GraphOptionItem> filterTypeOptions;
    private List<GraphOptionItem> companyOptions;
    private List<GraphOptionItem> regionOptions;
    private List<GraphOptionItem> monthOptions;
}
