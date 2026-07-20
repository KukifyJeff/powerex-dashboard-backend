package com.chng.powerexdashboardbackend.dto.home;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class HomePriceExtremeDTO {
    private BigDecimal price;
    private String companyName;
    private Integer genTypeId;
    private LocalDate priceDate;
}
