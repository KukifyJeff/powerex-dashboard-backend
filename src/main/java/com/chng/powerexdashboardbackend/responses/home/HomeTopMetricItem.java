package com.chng.powerexdashboardbackend.responses.home;

import lombok.Data;

import java.util.List;

@Data
public class HomeTopMetricItem {
    private String key;
    private String label;
    private String value;
    private String unit;
    private String hint;
    private List<String> details;
    private String accent;
}
