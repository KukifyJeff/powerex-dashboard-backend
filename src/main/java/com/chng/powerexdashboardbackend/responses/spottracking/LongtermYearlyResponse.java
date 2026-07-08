package com.chng.powerexdashboardbackend.responses.spottracking;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class LongtermYearlyResponse {

    /**
     * 公司ID
     */
    private Integer companyId;

    /**
     * 公司名称
     */
    private String companyName;

    /**
     * 月度成交电量（亿千瓦时）
     */
    private BigDecimal transactionAmount;

    /**
     * 月度交易电价（加权均价）
     */
    private BigDecimal transactionPrice;
}