package com.chng.powerexdashboardbackend.dto.weeklyreport;

import lombok.Data;

@Data
public class ChartSeriesDTO {
    private String name;
    private String type;
    private String color;
    private Boolean showSymbol;
    private Boolean smooth;
    private Boolean dashed;
    private Boolean showLabel;
    private Object data;
}
