package com.chng.powerexdashboardbackend.entities;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@TableName("spot_transactions")
public class SpotTransaction {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Integer companyId;

    private String companyName;

    private Integer genTypeId;

    private LocalDate date;

    private BigDecimal genAmount;

    private BigDecimal longtermAmount;

    private BigDecimal longtermPrice;

    private BigDecimal spotPrice;

    private BigDecimal chngSpotPrice;

    // ===== 计算字段（不落库） =====
    private BigDecimal longtermRevenue;

    private BigDecimal spotRevenue;
}