package com.chng.powerexdashboardbackend.responses.spottracking;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class SpotTrackingSummaryResponse {

    /** 公司ID */
    private Integer companyId;

    /** 公司名称 */
    private String companyName;

    /** 年度合约持仓占比 */
    private BigDecimal yearlyHoldingRate;

    /** 年度+月度合约持仓占比 */
    private BigDecimal totalHoldingRate;

    /** 年度交易电价 */
    private BigDecimal yearlyPrice;

    /** 月度交易电价 */
    private BigDecimal monthlyPrice;

    /** 中长期均价 */
    private BigDecimal longtermAvgPrice;

    /** 统一结算点实时均价 */
    private BigDecimal spotAvgPrice;

    /** 日清分均价 */
    private BigDecimal chngAvgPrice;

    /** 交易比价 */
    private BigDecimal priceDiff;

    /** 单位千瓦收入 */
    private BigDecimal unitCapacityIncome;
}