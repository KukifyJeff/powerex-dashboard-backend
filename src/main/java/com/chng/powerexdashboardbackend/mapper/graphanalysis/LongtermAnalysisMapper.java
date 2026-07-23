package com.chng.powerexdashboardbackend.mapper.graphanalysis;

import com.chng.powerexdashboardbackend.dto.weeklyreport.LongtermAmountPriceTrendDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface LongtermAnalysisMapper {

    List<String> selectLongtermMonthOptions();

    LongtermAmountPriceTrendDTO selectLongtermAnnualTrend(
            @Param("filterType") String filterType,
            @Param("filterIds") List<Integer> filterIds,
            @Param("contractStartDate") LocalDate contractStartDate,
            @Param("contractEndDate") LocalDate contractEndDate
    );

    List<LongtermAmountPriceTrendDTO> selectLongtermMonthlyTrend(
            @Param("filterType") String filterType,
            @Param("filterIds") List<Integer> filterIds,
            @Param("contractStartDate") LocalDate contractStartDate,
            @Param("contractEndDate") LocalDate contractEndDate
    );
}
