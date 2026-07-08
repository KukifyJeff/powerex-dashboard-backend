package com.chng.powerexdashboardbackend.entities;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("companies")
public class Company {

        @TableId(type = IdType.AUTO)
        private Integer id;

        private String name;

        private Integer regionId;

        private BigDecimal capacity;

        private Boolean isCoal;
}