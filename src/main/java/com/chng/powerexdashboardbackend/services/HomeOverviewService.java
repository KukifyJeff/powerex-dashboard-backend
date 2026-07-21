package com.chng.powerexdashboardbackend.services;

import com.chng.powerexdashboardbackend.dto.home.HomeImportSnapshotDTO;
import com.chng.powerexdashboardbackend.dto.home.HomeLongtermAssetStatDTO;
import com.chng.powerexdashboardbackend.dto.home.HomePriceExtremeDTO;
import com.chng.powerexdashboardbackend.dto.home.HomeSpotAssetStatDTO;
import com.chng.powerexdashboardbackend.enums.GenTypeEnum;
import com.chng.powerexdashboardbackend.mapper.home.HomeOverviewMapper;
import com.chng.powerexdashboardbackend.responses.home.HomeDataAssetItem;
import com.chng.powerexdashboardbackend.responses.home.HomeDataStatus;
import com.chng.powerexdashboardbackend.responses.home.HomeOverviewData;
import com.chng.powerexdashboardbackend.responses.home.HomeOverviewResponse;
import com.chng.powerexdashboardbackend.responses.home.HomeRecentImportItem;
import com.chng.powerexdashboardbackend.responses.home.HomeTopMetricItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class HomeOverviewService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final HomeOverviewMapper homeOverviewMapper;

    public HomeOverviewResponse getHomeOverview(LocalDate asOfDate) {
        LocalDate resolvedAsOfDate = resolveAsOfDate(asOfDate);
        LocalDate windowStart = resolvedAsOfDate.minusDays(29);

        LocalDate latestSpotDate = homeOverviewMapper.getLatestSpotDate(resolvedAsOfDate);
        String latestLongtermPeriod = defaultText(homeOverviewMapper.getLatestLongtermPeriod(resolvedAsOfDate));
        List<HomeSpotAssetStatDTO> spotStats = homeOverviewMapper.getSpotAssetStats(resolvedAsOfDate);
        HomeLongtermAssetStatDTO longtermStat = homeOverviewMapper.getLongtermAssetStat(resolvedAsOfDate);
        Long totalSpotCount = nullSafeLong(homeOverviewMapper.countTotalSpotRecords(resolvedAsOfDate));
        Long totalLongtermCount = nullSafeLong(homeOverviewMapper.countTotalLongtermRecords(resolvedAsOfDate));
        BigDecimal avgSpotPrice30d = scale2(homeOverviewMapper.getAverageSpotPrice30d(windowStart, resolvedAsOfDate));
        HomePriceExtremeDTO max30d = homeOverviewMapper.getMaxChngSpotPrice30d(windowStart, resolvedAsOfDate);
        HomePriceExtremeDTO min30d = homeOverviewMapper.getMinChngSpotPrice30d(windowStart, resolvedAsOfDate);
        int distinctDays = nullSafeInt(homeOverviewMapper.countDistinctSpotDays(windowStart, resolvedAsOfDate));
        int missingRecordCount = nullSafeInt(homeOverviewMapper.countMissingSpotRecords(windowStart, resolvedAsOfDate));

        Map<Integer, HomeSpotAssetStatDTO> statByGenType = new HashMap<>();
        for (HomeSpotAssetStatDTO stat : spotStats) {
            statByGenType.put(stat.getGenTypeId(), stat);
        }

        HomeOverviewData data = new HomeOverviewData();
        data.setPageTitle("电力交易数据分析总览");
        data.setPageSubtitle("面向交易人员的首页工作台，优先展示数据可用性、市场概况、导入状态与高频入口。");
        data.setTopMetrics(buildTopMetrics(resolvedAsOfDate, latestSpotDate, longtermStat, totalSpotCount, totalLongtermCount, avgSpotPrice30d, max30d, min30d, windowStart));
        data.setDataAssets(buildDataAssets(statByGenType, longtermStat));
        data.setDataStatus(buildDataStatus(distinctDays, latestSpotDate, latestLongtermPeriod, missingRecordCount));
        data.setRecentImports(buildRecentImports(resolvedAsOfDate));
        data.setUpdatedAt(LocalDateTime.now().format(DATE_TIME_FMT));

        HomeOverviewResponse response = new HomeOverviewResponse();
        response.setSuccess(true);
        response.setMessage("OK");
        response.setData(data);
        return response;
    }

    private LocalDate resolveAsOfDate(LocalDate asOfDate) {
        if (asOfDate != null) {
            return asOfDate;
        }
        LocalDate latestSpotDate = homeOverviewMapper.getLatestSpotDate(LocalDate.now());
        return latestSpotDate == null ? LocalDate.now() : latestSpotDate;
    }

    private List<HomeTopMetricItem> buildTopMetrics(
            LocalDate asOfDate,
            LocalDate latestSpotDate,
            HomeLongtermAssetStatDTO longtermStat,
            Long totalSpotCount,
            Long totalLongtermCount,
            BigDecimal avgSpotPrice30d,
            HomePriceExtremeDTO max30d,
            HomePriceExtremeDTO min30d,
            LocalDate windowStart
    ) {
        List<HomeTopMetricItem> metrics = new ArrayList<>();

        HomeTopMetricItem latestSpot = new HomeTopMetricItem();
        latestSpot.setKey("latest-spot-date");
        latestSpot.setLabel("现货最新数据");
        latestSpot.setValue(latestSpotDate == null ? "-" : latestSpotDate.format(DATE_FMT));
        long delayDays = latestSpotDate == null ? 0 : Math.max(0, ChronoUnit.DAYS.between(latestSpotDate, asOfDate));
        latestSpot.setHint("较计划延迟 " + delayDays + " 天");
        latestSpot.setAccent("blue");
        metrics.add(latestSpot);

        HomeTopMetricItem longterm = new HomeTopMetricItem();
        longterm.setKey("longterm-period");
        longterm.setLabel("中长期覆盖周期");
        longterm.setValue(formatLongtermPeriodRange(longtermStat));
        longterm.setHint("月度连续完整");
        longterm.setAccent("teal");
        metrics.add(longterm);

        HomeTopMetricItem scale = new HomeTopMetricItem();
        scale.setKey("asset-scale");
        scale.setLabel("数据资产规模");
        scale.setValue(formatWan(totalSpotCount + totalLongtermCount));
        scale.setHint("含现货与中长期");
        scale.setAccent("slate");
        metrics.add(scale);

        HomeTopMetricItem avg30 = new HomeTopMetricItem();
        avg30.setKey("avg-30d");
        avg30.setLabel("近30天现货市场均价");
        avg30.setValue(formatPrice(avgSpotPrice30d));
        avg30.setUnit("元/MWh");
        avg30.setDetails(List.of("日期范围: " + windowStart.format(DATE_FMT) + " 至 " + asOfDate.format(DATE_FMT)));
        avg30.setAccent("cyan");
        metrics.add(avg30);

        HomeTopMetricItem maxMetric = new HomeTopMetricItem();
        maxMetric.setKey("max-30d");
        maxMetric.setLabel("近30天最高日清分价");
        maxMetric.setValue(formatPrice(max30d == null ? BigDecimal.ZERO : max30d.getPrice()));
        maxMetric.setUnit("元/MWh");
        maxMetric.setDetails(buildExtremeDetails(max30d));
        maxMetric.setAccent("gold");
        metrics.add(maxMetric);

        HomeTopMetricItem minMetric = new HomeTopMetricItem();
        minMetric.setKey("min-30d");
        minMetric.setLabel("近30天最低日清分价");
        minMetric.setValue(formatPrice(min30d == null ? BigDecimal.ZERO : min30d.getPrice()));
        minMetric.setUnit("元/MWh");
        minMetric.setDetails(buildExtremeDetails(min30d));
        minMetric.setAccent("rose");
        metrics.add(minMetric);

        return metrics;
    }

    private List<HomeDataAssetItem> buildDataAssets(Map<Integer, HomeSpotAssetStatDTO> statByGenType, HomeLongtermAssetStatDTO longtermStat) {
        List<HomeDataAssetItem> assets = new ArrayList<>();
        assets.add(buildSpotAsset("coal", "煤机现货", "coal", statByGenType.get(1)));
        assets.add(buildSpotAsset("wind", "风电现货", "wind", statByGenType.get(3)));
        assets.add(buildSpotAsset("solar", "光伏现货", "solar", statByGenType.get(2)));

        HomeDataAssetItem longterm = new HomeDataAssetItem();
        longterm.setAssetType("longterm");
        longterm.setTitle("中长期市场");
        longterm.setRange(formatLongtermRange(longtermStat));
        longterm.setLatest(longtermStat == null || longtermStat.getMaxPeriod() == null ? "-" : longtermStat.getMaxPeriod());
        longterm.setCount(formatWan(longtermStat == null ? 0 : nullSafeLong(longtermStat.getRecordCount())));
        longterm.setTone("ledger");
        assets.add(longterm);
        return assets;
    }

    private HomeDataStatus buildDataStatus(int distinctDays, LocalDate latestSpotDate, String latestLongtermPeriod, int missingRecordCount) {
        HomeDataStatus status = new HomeDataStatus();
        status.setSpotCoveragePercent(
                scale1(BigDecimal.valueOf(distinctDays)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(30), 4, RoundingMode.HALF_UP))
        );
        status.setLatestSpotDate(latestSpotDate == null ? "-" : latestSpotDate.format(DATE_FMT));
        status.setLatestLongtermPeriod(latestLongtermPeriod);
        status.setMissingRecordCount(missingRecordCount);
        return status;
    }

    private List<HomeRecentImportItem> buildRecentImports(LocalDate asOfDate) {
        List<HomeImportSnapshotDTO> all = new ArrayList<>();
        all.addAll(homeOverviewMapper.getRecentSpotImports(asOfDate));
        all.addAll(homeOverviewMapper.getRecentLongtermImports(asOfDate));
        all.sort(Comparator.comparing(HomeImportSnapshotDTO::getImportDate, Comparator.nullsLast(Comparator.reverseOrder())));
        if (all.size() > 4) {
            all = all.subList(0, 4);
        }

        List<HomeRecentImportItem> items = new ArrayList<>();
        int idx = 1;
        for (HomeImportSnapshotDTO raw : all) {
            HomeRecentImportItem item = new HomeRecentImportItem();
            item.setId(String.valueOf(idx++));
            item.setType(raw.getType());
            item.setTime(raw.getImportDate() == null ? "-" : raw.getImportDate().format(DATE_FMT) + " 21:00");
            item.setAdded((int) Math.min(Integer.MAX_VALUE, nullSafeLong(raw.getAdded())));
            item.setUpdated(0);
            item.setStatus("success");
            items.add(item);
        }
        return items;
    }

    private HomeDataAssetItem buildSpotAsset(String assetType, String title, String tone, HomeSpotAssetStatDTO stat) {
        HomeDataAssetItem item = new HomeDataAssetItem();
        item.setAssetType(assetType);
        item.setTitle(title);
        item.setRange(formatDateRange(stat == null ? null : stat.getMinDate(), stat == null ? null : stat.getMaxDate()));
        item.setLatest(stat == null || stat.getMaxDate() == null ? "-" : stat.getMaxDate().format(DATE_FMT));
        item.setCount(formatWan(stat == null ? 0 : nullSafeLong(stat.getRecordCount())));
        item.setTone(tone);
        return item;
    }

    private List<String> buildExtremeDetails(HomePriceExtremeDTO dto) {
        if (dto == null) {
            return List.of("公司: -", "类型: -", "日期: -");
        }
        return List.of(
                "公司: " + defaultText(dto.getCompanyName()),
                "类型: " + genTypeName(dto.getGenTypeId()),
                "日期: " + (dto.getPriceDate() == null ? "-" : dto.getPriceDate().format(DATE_FMT))
        );
    }

    private String genTypeName(Integer genTypeId) {
        if (genTypeId == null) {
            return "-";
        }
        try {
            GenTypeEnum genType = GenTypeEnum.of(genTypeId);
            if (genType == GenTypeEnum.COAL) {
                return "燃煤";
            }
            return genType.getName();
        } catch (IllegalArgumentException ex) {
            return "其他";
        }
    }

    private String formatLongtermPeriodRange(HomeLongtermAssetStatDTO stat) {
        if (stat == null || stat.getMinPeriod() == null || stat.getMaxPeriod() == null) {
            return "-";
        }
        return toZhMonth(stat.getMinPeriod()) + "-" + toZhMonth(stat.getMaxPeriod());
    }

    private String formatLongtermRange(HomeLongtermAssetStatDTO stat) {
        if (stat == null || stat.getMinPeriod() == null || stat.getMaxPeriod() == null) {
            return "-";
        }
        return stat.getMinPeriod() + " 至 " + stat.getMaxPeriod();
    }

    private String toZhMonth(String yyyyMm) {
        if (yyyyMm == null || yyyyMm.length() != 7) {
            return "-";
        }
        String[] parts = yyyyMm.split("-");
        if (parts.length != 2) {
            return "-";
        }
        return parts[0] + "年" + Integer.parseInt(parts[1]) + "月";
    }

    private String formatDateRange(LocalDate start, LocalDate end) {
        if (start == null || end == null) {
            return "-";
        }
        return start.format(DATE_FMT) + " 至 " + end.format(DATE_FMT);
    }

    private String formatWan(long count) {
        BigDecimal value = BigDecimal.valueOf(count).divide(BigDecimal.valueOf(10000), 1, RoundingMode.HALF_UP);
        return value.toPlainString() + " 万条";
    }

    private String formatPrice(BigDecimal value) {
        return scale2(value).toPlainString();
    }

    private BigDecimal scale2(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private long nullSafeLong(Long value) {
        return value == null ? 0L : value;
    }

    private int nullSafeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private String defaultText(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private Double scale1(BigDecimal value) {
        if (value == null) {
            return 0D;
        }
        return value.setScale(1, RoundingMode.HALF_UP).doubleValue();
    }
}
