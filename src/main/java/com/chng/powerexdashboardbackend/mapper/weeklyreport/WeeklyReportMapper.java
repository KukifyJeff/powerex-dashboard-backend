package com.chng.powerexdashboardbackend.mapper.weeklyreport;

import com.chng.powerexdashboardbackend.dto.weeklyreport.CompanyDailyPriceDTO;
import com.chng.powerexdashboardbackend.dto.weeklyreport.RegionalCompanyDailySpotPriceDTO;
import com.chng.powerexdashboardbackend.dto.weeklyreport.ProvincialSpotCompanyDailyDTO;
import com.chng.powerexdashboardbackend.dto.weeklyreport.LongtermAmountPriceTrendDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface WeeklyReportMapper {

    LocalDate getLatestSpotDate();

    List<ProvincialSpotCompanyDailyDTO> getProvincialCompanyDailyPrices(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    List<CompanyDailyPriceDTO> getCompanyDailyPriceTrend(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    List<RegionalCompanyDailySpotPriceDTO> getRegionalCompanyDailySpotPriceTrend(
            @Param("regionId") Integer regionId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    LongtermAmountPriceTrendDTO getLongtermAnnualTrend(
            @Param("contractStartDate") LocalDate contractStartDate,
            @Param("contractEndDate") LocalDate contractEndDate
    );

    List<LongtermAmountPriceTrendDTO> getLongtermMonthlyTrend(
            @Param("contractStartDate") LocalDate contractStartDate,
            @Param("contractEndDate") LocalDate contractEndDate
    );
}
