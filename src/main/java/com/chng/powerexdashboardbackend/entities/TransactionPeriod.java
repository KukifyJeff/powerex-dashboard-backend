package com.chng.powerexdashboardbackend.entities;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("transaction_periods")
public class TransactionPeriod {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private String name;
}