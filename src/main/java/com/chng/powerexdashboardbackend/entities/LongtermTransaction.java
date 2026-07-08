package com.chng.powerexdashboardbackend.entities;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@TableName("longterm_transactions")
public class LongtermTransaction {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Integer companyId;

    private Integer genTypeId;

    private Integer transactionTypeId;

    private Integer transactionPeriodId;

    private LocalDate contractStartDate;

    private LocalDate contractEndDate;

    private LocalDate transactionDate;

    private BigDecimal transactionAmount;

    private BigDecimal transactionPrice;

    private BigDecimal chngTransactionAmount;

    private BigDecimal chngAvgPrice;

    private BigDecimal marketAvgPrice;

    private Boolean isGreen;

    private Boolean isCheap;
}