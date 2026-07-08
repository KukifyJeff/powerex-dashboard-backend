package com.chng.powerexdashboardbackend.responses.spottracking;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class SpotDataResponse {

    /**
     * 公司信息
     */
    private Integer companyId;
    private String companyName;

    /**
     * 上网电量（亿千瓦时）
     */
    private BigDecimal genAmount;

    /**
     * 中长期合约电量（亿千瓦时）
     */
    private BigDecimal longtermAmount;

    /**
     * 中长期均价
     */
    private BigDecimal longtermAvgPrice;

    /**
     * 持仓率
     */
    private BigDecimal holdingRate;

    /**
     * 统一结算点实时均价（加权）
     */
    private BigDecimal spotAvgPrice;

    /**
     * 日清分均价（加权）
     */
    private BigDecimal chngAvgPrice;
}