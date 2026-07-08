package com.chng.powerexdashboardbackend.dto.spottracking;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class SpotDataDTO {

    /**
     * 公司信息
     */
    private Long companyId;
    private String companyName;

    /**
     * 上网电量（SUM）
     */
    private BigDecimal genAmount;

    /**
     * 中长期合约电量（SUM）
     */
    private BigDecimal longtermAmount;

    /**
     * SUM(spot_price * gen_amount)
     */
    private BigDecimal spotRevenueSum;

    /**
     * SUM(longterm_price * longterm_amount)
     */
    private BigDecimal longtermRevenueSum;

    /**
     * SUM(chng_price * gen_amount)
     */
    private BigDecimal chngRevenueSum;

    /**
     * 单位千瓦收入计算需要
     */
    private BigDecimal capacity;
    private BigDecimal spotAvgPrice;
}