package com.chng.powerexdashboardbackend.dto.spottracking;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class LongtermYearlyDTO {

    private Long companyId;
    private String companyName;

    private BigDecimal transactionAmount;
    private BigDecimal transactionPrice;
}
