package com.chng.powerexdashboardbackend.mapper.graphanalysis;

import com.chng.powerexdashboardbackend.dto.graphanalysis.IdNameDTO;
import com.chng.powerexdashboardbackend.dto.graphanalysis.SpotAnalysisDailyPriceDTO;
import com.chng.powerexdashboardbackend.dto.graphanalysis.SpotDateRangeDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface SpotAnalysisMapper {

    List<IdNameDTO> selectCompanyOptions();

    List<Integer> selectRegionIds();

    SpotDateRangeDTO selectSpotDateRange();

    List<SpotAnalysisDailyPriceDTO> selectDailyAvgSpotPrice(
            @Param("filterType") String filterType,
            @Param("filterIds") List<Integer> filterIds,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );
}
