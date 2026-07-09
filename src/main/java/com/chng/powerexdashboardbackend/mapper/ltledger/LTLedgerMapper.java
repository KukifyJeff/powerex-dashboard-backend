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
        @Param("transactionTypeIds") List<Integer> transactionTypeIds,
        @Param("transactionPeriodIds") List<Integer> transactionPeriodIds,
        @Param("contractStartDate") String contractStartDate,
        @Param("contractEndDate") String contractEndDate,
        @Param("greenPowerList") List<Integer> greenPowerList
    );

    List<LTLedgerDTO> getLedgerDetail(
        @Param("companyId") Long companyId,
        @Param("genTypeIds") List<Integer> genTypeIds,
        @Param("transactionTypeIds") List<Integer> transactionTypeIds,
        @Param("transactionPeriodIds") List<Integer> transactionPeriodIds,
        @Param("contractStartDate") String contractStartDate,
        @Param("contractEndDate") String contractEndDate,
        @Param("greenPowerList") List<Integer> greenPowerList
    );

    LTLedgerSummaryDTO getLedgerSummary(
        @Param("genTypeIds") List<Integer> genTypeIds,
        @Param("transactionTypeIds") List<Integer> transactionTypeIds,
        @Param("transactionPeriodIds") List<Integer> transactionPeriodIds,
        @Param("contractStartDate") String contractStartDate,
        @Param("contractEndDate") String contractEndDate,
        @Param("greenPowerList") List<Integer> greenPowerList
    );

    List<LTLedgerTrendDTO> getLedgerTrend(
        @Param("genTypeIds") List<Integer> genTypeIds,
        @Param("transactionTypeIds") List<Integer> transactionTypeIds,
        @Param("transactionPeriodIds") List<Integer> transactionPeriodIds,
        @Param("contractStartDate") String contractStartDate,
        @Param("contractEndDate") String contractEndDate,
        @Param("greenPowerList") List<Integer> greenPowerList
    );

    // helper: name -> id

    // filter options
    List<Integer> getTransactionTypes(
        @Param("genTypeIds") List<Integer> genTypeIds,
        @Param("transactionTypeIds") List<Integer> transactionTypeIds,
        @Param("transactionPeriodIds") List<Integer> transactionPeriodIds,
        @Param("contractStartDate") String contractStartDate,
        @Param("contractEndDate") String contractEndDate,
        @Param("greenPowerList") List<Integer> greenPowerList
    );
    List<Integer> getGenTypes(
        @Param("genTypeIds") List<Integer> genTypeIds,
        @Param("transactionTypeIds") List<Integer> transactionTypeIds,
        @Param("transactionPeriodIds") List<Integer> transactionPeriodIds,
        @Param("contractStartDate") String contractStartDate,
        @Param("contractEndDate") String contractEndDate,
        @Param("greenPowerList") List<Integer> greenPowerList
    );
    List<Integer> getTransactionPeriods(
        @Param("genTypeIds") List<Integer> genTypeIds,
        @Param("transactionTypeIds") List<Integer> transactionTypeIds,
        @Param("transactionPeriodIds") List<Integer> transactionPeriodIds,
        @Param("contractStartDate") String contractStartDate,
        @Param("contractEndDate") String contractEndDate,
        @Param("greenPowerList") List<Integer> greenPowerList
    );
    List<Integer> getGreenPowerOptions(
        @Param("genTypeIds") List<Integer> genTypeIds,
        @Param("transactionTypeIds") List<Integer> transactionTypeIds,
        @Param("transactionPeriodIds") List<Integer> transactionPeriodIds,
        @Param("contractStartDate") String contractStartDate,
        @Param("contractEndDate") String contractEndDate,
        @Param("greenPowerList") List<Integer> greenPowerList
    );
    String getMinContractDate(
        @Param("genTypeIds") List<Integer> genTypeIds,
        @Param("transactionTypeIds") List<Integer> transactionTypeIds,
        @Param("transactionPeriodIds") List<Integer> transactionPeriodIds,
        @Param("contractStartDate") String contractStartDate,
        @Param("contractEndDate") String contractEndDate,
        @Param("greenPowerList") List<Integer> greenPowerList
    );
    String getMaxContractDate(
        @Param("genTypeIds") List<Integer> genTypeIds,
        @Param("transactionTypeIds") List<Integer> transactionTypeIds,
        @Param("transactionPeriodIds") List<Integer> transactionPeriodIds,
        @Param("contractStartDate") String contractStartDate,
        @Param("contractEndDate") String contractEndDate,
        @Param("greenPowerList") List<Integer> greenPowerList
    );
}