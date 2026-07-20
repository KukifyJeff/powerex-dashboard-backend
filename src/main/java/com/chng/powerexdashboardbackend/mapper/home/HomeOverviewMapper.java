package com.chng.powerexdashboardbackend.mapper.home;

import com.chng.powerexdashboardbackend.dto.home.HomeImportSnapshotDTO;
import com.chng.powerexdashboardbackend.dto.home.HomeLongtermAssetStatDTO;
import com.chng.powerexdashboardbackend.dto.home.HomePriceExtremeDTO;
import com.chng.powerexdashboardbackend.dto.home.HomeSpotAssetStatDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Mapper
public interface HomeOverviewMapper {

    LocalDate getLatestSpotDate(@Param("asOfDate") LocalDate asOfDate);

    String getLatestLongtermPeriod(@Param("asOfDate") LocalDate asOfDate);

    List<HomeSpotAssetStatDTO> getSpotAssetStats(@Param("asOfDate") LocalDate asOfDate);

    HomeLongtermAssetStatDTO getLongtermAssetStat(@Param("asOfDate") LocalDate asOfDate);

    Long countTotalSpotRecords(@Param("asOfDate") LocalDate asOfDate);

    Long countTotalLongtermRecords(@Param("asOfDate") LocalDate asOfDate);

    BigDecimal getAverageSpotPrice30d(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    HomePriceExtremeDTO getMaxChngSpotPrice30d(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    HomePriceExtremeDTO getMinChngSpotPrice30d(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    Integer countDistinctSpotDays(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    Integer countMissingSpotRecords(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    List<HomeImportSnapshotDTO> getRecentSpotImports(@Param("asOfDate") LocalDate asOfDate);

    List<HomeImportSnapshotDTO> getRecentLongtermImports(@Param("asOfDate") LocalDate asOfDate);
}
