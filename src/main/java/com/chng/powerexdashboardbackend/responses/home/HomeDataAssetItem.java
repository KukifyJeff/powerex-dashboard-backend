package com.chng.powerexdashboardbackend.responses.home;

import lombok.Data;

@Data
public class HomeDataAssetItem {
    private String assetType;
    private String title;
    private String range;
    private String latest;
    private String count;
    private String tone;
}
