package com.chng.powerexdashboardbackend.dto.ltledger;

import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.math.BigDecimal;

@Data
public class LTLedgerDTO {
    private Long id;
    private Integer transactionId;

    private Long companyId;
    private String companyName;
    private String place;
    private LocalDate transactionDate;
    private String transactionName;

    private Integer transactionTypeId;
    private String transactionTypeName;

    private String outsendProvince;

    private Integer genTypeId;
    private String genTypeName;

    private Integer transactionPeriodId;
    private String transactionPeriodName;

    private Integer transactionStartYear;
    private Integer transactionEndYear;

    private LocalDate contractStartDate;
    private LocalDate contractEndDate;

    private Boolean isGreen;
    private Boolean isCheap;

    private BigDecimal basePrice;
    private BigDecimal marketSize;
    private BigDecimal marketParticipationCapacity;
    private BigDecimal marketAvgPrice;
    private BigDecimal chngParticipationCapacity;
    private BigDecimal chngTransactionAmount;
    private BigDecimal chngTradedPrice;
    private BigDecimal envPremium;

    private String dataSource;
    private String note;
    private LocalDateTime createdAt;

}