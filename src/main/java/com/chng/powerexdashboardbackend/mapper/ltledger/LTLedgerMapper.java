package com.chng.powerexdashboardbackend.mapper.ltledger;

import com.chng.powerexdashboardbackend.dto.ltledger.LTLedgerSummaryDTO;
import com.chng.powerexdashboardbackend.dto.ltledger.LTLedgerTrendDTO;
import com.chng.powerexdashboardbackend.dto.ltledger.LTLedgerDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface LTLedgerMapper {

    List<LTLedgerDTO> getLedgerPivot(
        @Param("genTypeIds") List<Integer> genTypeIds,
        @Param("companyIds") List<Long> companyIds,
        @Param("transactionTypeIds") List<Integer> transactionTypeIds,
        @Param("transactionPeriodIds") List<Integer> transactionPeriodIds,
        @Param("contractStartDate") String contractStartDate,
        @Param("contractEndDate") String contractEndDate,
        @Param("greenPowerList") List<String> greenPowerList
    );

    List<LTLedgerDTO> getLedgerDetail(
        @Param("genTypeIds") List<Integer> genTypeIds,
        @Param("companyIds") List<Long> companyIds,
        @Param("transactionTypeIds") List<Integer> transactionTypeIds,
        @Param("transactionPeriodIds") List<Integer> transactionPeriodIds,
        @Param("contractStartDate") String contractStartDate,
        @Param("contractEndDate") String contractEndDate,
        @Param("greenPowerList") List<String> greenPowerList
    );

    LTLedgerSummaryDTO getLedgerSummary(
        @Param("genTypeIds") List<Integer> genTypeIds,
        @Param("companyIds") List<Long> companyIds,
        @Param("transactionTypeIds") List<Integer> transactionTypeIds,
        @Param("transactionPeriodIds") List<Integer> transactionPeriodIds,
        @Param("contractStartDate") String contractStartDate,
        @Param("contractEndDate") String contractEndDate,
        @Param("greenPowerList") List<String> greenPowerList
    );

    List<LTLedgerTrendDTO> getLedgerTrend(
        @Param("genTypeIds") List<Integer> genTypeIds,
        @Param("companyIds") List<Long> companyIds,
        @Param("transactionTypeIds") List<Integer> transactionTypeIds,
        @Param("transactionPeriodIds") List<Integer> transactionPeriodIds,
        @Param("contractStartDate") String contractStartDate,
        @Param("contractEndDate") String contractEndDate,
        @Param("greenPowerList") List<String> greenPowerList
    );

    // helper: name -> id
    List<Long> getCompanyIdsByNames(@Param("companyNames") List<String> companyNames);
    List<Integer> getGenTypeIdsByNames(@Param("genTypeNames") List<String> genTypeNames);
    List<Integer> getTransactionTypeIdsByNames(@Param("transactionTypes") List<String> transactionTypes);
    List<Integer> getTransactionPeriodIdsByNames(@Param("transactionPeriods") List<String> transactionPeriods);

    // filter options
    List<String> getTransactionTypes();
    List<String> getGenTypes();
    List<String> getTransactionPeriods();
    List<String> getGreenPowerOptions();
    String getMinContractDate();
    String getMaxContractDate();
}