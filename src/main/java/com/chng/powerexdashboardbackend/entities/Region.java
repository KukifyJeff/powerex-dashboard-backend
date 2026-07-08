package com.chng.powerexdashboardbackend.entities;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("regions")
public class Region {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private String name;
}