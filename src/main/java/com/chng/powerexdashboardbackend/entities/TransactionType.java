package com.chng.powerexdashboardbackend.entities;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("transaction_types")
public class TransactionType {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private String name;
}