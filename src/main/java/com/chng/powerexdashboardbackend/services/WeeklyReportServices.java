package com.chng.powerexdashboardbackend.services;

import com.chng.powerexdashboardbackend.dto.weeklyreport.ChartSeriesDTO;
import com.chng.powerexdashboardbackend.dto.weeklyreport.CompanyDailyPriceDTO;
import com.chng.powerexdashboardbackend.dto.weeklyreport.CompanyPriceTrendResponseDTO;
import com.chng.powerexdashboardbackend.dto.weeklyreport.CompanyPriceTrendWeekDTO;
import com.chng.powerexdashboardbackend.dto.weeklyreport.LongtermAmountPriceTrendResponseDTO;
import com.chng.powerexdashboardbackend.dto.weeklyreport.RegionalCompanyDailySpotPriceDTO;
import com.chng.powerexdashboardbackend.dto.weeklyreport.ProvincialSpotCompanyDailyDTO;
import com.chng.powerexdashboardbackend.dto.weeklyreport.ProvincialSpotTrendOptionsDTO;
import com.chng.powerexdashboardbackend.dto.weeklyreport.ProvincialSpotTrendResponseDTO;
import com.chng.powerexdashboardbackend.dto.weeklyreport.ProvincialSpotTrendWeekDTO;
import com.chng.powerexdashboardbackend.dto.weeklyreport.LongtermAmountPriceTrendDTO;
import com.chng.powerexdashboardbackend.dto.weeklyreport.RegionalSpotTrendCompanyDTO;
import com.chng.powerexdashboardbackend.dto.weeklyreport.RegionalSpotTrendResponseDTO;
import com.chng.powerexdashboardbackend.dto.weeklyreport.RegionalSpotTrendWeekDTO;
import com.chng.powerexdashboardbackend.mapper.weeklyreport.WeeklyReportMapper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
public class WeeklyReportServices {

    private static final int RECENT_WEEK_COUNT = 10;
    private final WeeklyReportMapper weeklyReportMapper;

    public ProvincialSpotTrendOptionsDTO getProvincialSpotTrendOptions() {
        LocalDate latestDate = weeklyReportMapper.getLatestSpotDate();
        ProvincialSpotTrendOptionsDTO response = new ProvincialSpotTrendOptionsDTO();
        if (latestDate == null) {
            response.setMaxWeekKey(null);
            response.setWeekOptions(new ArrayList<>());
            return response;
        }

        WeekTimeline timeline = buildWeekTimeline(latestDate);
        response.setMaxWeekKey(timeline.maxWeekKey());
        response.setWeekOptions(timeline.weekKeys());
        return response;
    }

    public ProvincialSpotTrendResponseDTO getProvincialSpotTrend(String lastDataWeekKey) {
        LocalDate latestDate = weeklyReportMapper.getLatestSpotDate();
        ProvincialSpotTrendResponseDTO response = new ProvincialSpotTrendResponseDTO();
        response.setUnit("元/MWh");
        response.setWeekRule("周六到下周五");
        response.setSeries(new ArrayList<>());
        response.setXAxis(new ArrayList<>());
        response.setWeeks(new ArrayList<>());
        if (latestDate == null) {
            response.setMaxWeekKey(null);
            response.setSelectedLastDataWeekKey(null);
            response.setAnnualCumulativeMarketAvgPrice(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            return response;
        }

        WeekTimeline timeline = buildWeekTimeline(latestDate);
        if (timeline.weekKeys().isEmpty()) {
            response.setMaxWeekKey(null);
            response.setSelectedLastDataWeekKey(null);
            response.setAnnualCumulativeMarketAvgPrice(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            return response;
        }

        String selectedWeekKey = lastDataWeekKey == null || lastDataWeekKey.isBlank()
                ? timeline.maxWeekKey()
                : lastDataWeekKey.trim().toUpperCase(Locale.ROOT);
        WeekWindow selectedWindow = timeline.weekWindowByKey().get(selectedWeekKey);
        if (selectedWindow == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid lastDataWeekKey: " + selectedWeekKey);
        }

        List<ProvincialSpotCompanyDailyDTO> companyDailyRows = weeklyReportMapper.getProvincialCompanyDailyPrices(
                LocalDate.of(latestDate.getYear(), 1, 1),
                selectedWindow.endDate()
        );
        Map<LocalDate, List<BigDecimal>> dailyMarketPrices = buildDailyMarketPrices(companyDailyRows);

        List<ProvincialSpotTrendWeekDTO> weeks = new ArrayList<>();
        List<String> xAxis = new ArrayList<>();
        for (WeekWindow window : timeline.recentWeekWindowsUpTo(selectedWeekKey, RECENT_WEEK_COUNT)) {
            List<BigDecimal> values = collectDailyValues(dailyMarketPrices, window.startDate(), window.endDate());
            weeks.add(buildWeekStat(window.startDate(), window.endDate(), values));
            xAxis.add(window.key());
        }

        response.setMaxWeekKey(timeline.maxWeekKey());
        response.setSelectedLastDataWeekKey(selectedWeekKey);
        response.setWeeks(weeks);
        response.setXAxis(xAxis);

        List<BigDecimal> minLine = weeks.stream()
                .map(ProvincialSpotTrendWeekDTO::getMinPrice)
                .toList();
        List<BigDecimal> maxLine = weeks.stream()
                .map(ProvincialSpotTrendWeekDTO::getMaxPrice)
                .toList();
        List<BigDecimal> averageLine = weeks.stream()
                .map(ProvincialSpotTrendWeekDTO::getAveragePrice)
                .toList();

        BigDecimal cumulativeAverage = calculateAverage(dailyMarketPrices.values().stream()
                .flatMap(List::stream)
                .toList());
        response.setAnnualCumulativeMarketAvgPrice(cumulativeAverage);

        ChartSeriesDTO minSeries = new ChartSeriesDTO();
        minSeries.setName("最小值");
        minSeries.setType("line");
        minSeries.setColor("#73C0DE");
        minSeries.setShowSymbol(Boolean.TRUE);
        minSeries.setSmooth(Boolean.FALSE);
        minSeries.setShowLabel(Boolean.TRUE);
        minSeries.setData(minLine);

        ChartSeriesDTO maxSeries = new ChartSeriesDTO();
        maxSeries.setName("最大值");
        maxSeries.setType("line");
        maxSeries.setColor("#FAC858");
        maxSeries.setShowSymbol(Boolean.TRUE);
        maxSeries.setSmooth(Boolean.FALSE);
        maxSeries.setShowLabel(Boolean.TRUE);
        maxSeries.setData(maxLine);

        ChartSeriesDTO averageSeries = new ChartSeriesDTO();
        averageSeries.setName("平均价格");
        averageSeries.setType("line");
        averageSeries.setColor("#91CC75");
        averageSeries.setShowSymbol(Boolean.TRUE);
        averageSeries.setSmooth(Boolean.FALSE);
        averageSeries.setShowLabel(Boolean.TRUE);
        averageSeries.setData(averageLine);

        ChartSeriesDTO benchmarkSeries = new ChartSeriesDTO();
        benchmarkSeries.setName("年累计实时均价");
        benchmarkSeries.setType("line");
        benchmarkSeries.setColor("#EE6666");
        benchmarkSeries.setShowSymbol(Boolean.FALSE);
        benchmarkSeries.setSmooth(Boolean.FALSE);
        benchmarkSeries.setDashed(Boolean.TRUE);
        benchmarkSeries.setShowLabel(Boolean.TRUE);
        benchmarkSeries.setData(repeatValue(cumulativeAverage, weeks.size()));

        response.getSeries().add(minSeries);
        response.getSeries().add(maxSeries);
        response.getSeries().add(averageSeries);
        response.getSeries().add(benchmarkSeries);
        return response;
    }

    public CompanyPriceTrendResponseDTO getCompanyPriceTrend(String lastDataWeekKey) {
        LocalDate latestDate = weeklyReportMapper.getLatestSpotDate();
        CompanyPriceTrendResponseDTO response = new CompanyPriceTrendResponseDTO();
        response.setUnit("元/MWh");
        response.setWeekRule("周六到下周五");
        response.setSeries(new ArrayList<>());
        response.setXAxis(new ArrayList<>());
        response.setWeeks(new ArrayList<>());
        if (latestDate == null) {
            response.setMaxWeekKey(null);
            response.setSelectedLastDataWeekKey(null);
            return response;
        }

        WeekTimeline timeline = buildWeekTimeline(latestDate);
        if (timeline.weekKeys().isEmpty()) {
            response.setMaxWeekKey(null);
            response.setSelectedLastDataWeekKey(null);
            return response;
        }

        String selectedWeekKey = lastDataWeekKey == null || lastDataWeekKey.isBlank()
                ? timeline.maxWeekKey()
                : lastDataWeekKey.trim().toUpperCase(Locale.ROOT);
        WeekWindow selectedWindow = timeline.weekWindowByKey().get(selectedWeekKey);
        if (selectedWindow == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid lastDataWeekKey: " + selectedWeekKey);
        }

        List<CompanyDailyPriceDTO> dailyRows = weeklyReportMapper.getCompanyDailyPriceTrend(
                LocalDate.of(latestDate.getYear(), 1, 1),
                selectedWindow.endDate()
        );
        Map<LocalDate, CompanyDailyPriceDTO> dailyMap = new TreeMap<>();
        for (CompanyDailyPriceDTO row : dailyRows) {
            if (row.getPriceDate() != null) {
                dailyMap.put(row.getPriceDate(), row);
            }
        }

        List<CompanyPriceTrendWeekDTO> weeks = new ArrayList<>();
        List<String> xAxis = new ArrayList<>();
        for (WeekWindow window : timeline.recentWeekWindowsUpTo(selectedWeekKey, RECENT_WEEK_COUNT)) {
            weeks.add(buildCompanyWeekStat(window.startDate(), window.endDate(), dailyMap));
            xAxis.add(window.key());
        }

        response.setMaxWeekKey(timeline.maxWeekKey());
        response.setSelectedLastDataWeekKey(selectedWeekKey);
        response.setWeeks(weeks);
        response.setXAxis(xAxis);

        ChartSeriesDTO marketSeries = new ChartSeriesDTO();
        marketSeries.setName("现货实时价（市场均价）");
        marketSeries.setType("line");
        marketSeries.setColor("#5470C6");
        marketSeries.setShowSymbol(Boolean.TRUE);
        marketSeries.setSmooth(Boolean.FALSE);
        marketSeries.setShowLabel(Boolean.TRUE);
        marketSeries.setData(weeks.stream().map(CompanyPriceTrendWeekDTO::getMarketAvgPrice).toList());

        ChartSeriesDTO coalSeries = new ChartSeriesDTO();
        coalSeries.setName("煤电清分价");
        coalSeries.setType("line");
        coalSeries.setColor("#FAC858");
        coalSeries.setShowSymbol(Boolean.TRUE);
        coalSeries.setSmooth(Boolean.FALSE);
        coalSeries.setShowLabel(Boolean.TRUE);
        coalSeries.setData(weeks.stream().map(CompanyPriceTrendWeekDTO::getCoalChngPrice).toList());

        ChartSeriesDTO windSeries = new ChartSeriesDTO();
        windSeries.setName("风电清分价");
        windSeries.setType("line");
        windSeries.setColor("#73C0DE");
        windSeries.setShowSymbol(Boolean.TRUE);
        windSeries.setSmooth(Boolean.FALSE);
        windSeries.setShowLabel(Boolean.TRUE);
        windSeries.setData(weeks.stream().map(CompanyPriceTrendWeekDTO::getWindChngPrice).toList());

        ChartSeriesDTO solarSeries = new ChartSeriesDTO();
        solarSeries.setName("光伏清分价");
        solarSeries.setType("line");
        solarSeries.setColor("#91CC75");
        solarSeries.setShowSymbol(Boolean.TRUE);
        solarSeries.setSmooth(Boolean.FALSE);
        solarSeries.setShowLabel(Boolean.TRUE);
        solarSeries.setData(weeks.stream().map(CompanyPriceTrendWeekDTO::getSolarChngPrice).toList());

        response.getSeries().add(marketSeries);
        response.getSeries().add(coalSeries);
        response.getSeries().add(windSeries);
        response.getSeries().add(solarSeries);
        return response;
    }

    public LongtermAmountPriceTrendResponseDTO getLongtermAmountPriceTrend(String lastDataWeekKey) {
        LocalDate latestDate = weeklyReportMapper.getLatestSpotDate();
        LongtermAmountPriceTrendResponseDTO response = new LongtermAmountPriceTrendResponseDTO();
        response.setUnitAmount("亿千瓦时");
        response.setUnitPrice("元/MWh");
        response.setSeries(new ArrayList<>());
        response.setXAxis(new ArrayList<>());
        response.setPeriods(new ArrayList<>());
        if (latestDate == null) {
            response.setMaxWeekKey(null);
            response.setSelectedLastDataWeekKey(null);
            return response;
        }

        WeekTimeline timeline = buildWeekTimeline(latestDate);
        if (timeline.weekKeys().isEmpty()) {
            response.setMaxWeekKey(null);
            response.setSelectedLastDataWeekKey(null);
            return response;
        }

        String selectedWeekKey = lastDataWeekKey == null || lastDataWeekKey.isBlank()
                ? timeline.maxWeekKey()
                : lastDataWeekKey.trim().toUpperCase(Locale.ROOT);
        WeekWindow selectedWindow = timeline.weekWindowByKey().get(selectedWeekKey);
        if (selectedWindow == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid lastDataWeekKey: " + selectedWeekKey);
        }

        LocalDate contractStartDate = LocalDate.of(selectedWindow.startDate().getYear(), 1, 1);
        LocalDate monthEnd = YearMonth.from(selectedWindow.endDate()).atEndOfMonth();
        LocalDate yearEnd = LocalDate.of(selectedWindow.startDate().getYear(), 12, 31);

        List<LongtermAmountPriceTrendDTO> periods = new ArrayList<>();
        LongtermAmountPriceTrendDTO annual = weeklyReportMapper.getLongtermAnnualTrend(
                contractStartDate, yearEnd
        );
        if (annual != null) {
            periods.add(annual);
        }
        periods.addAll(weeklyReportMapper.getLongtermMonthlyTrend(
                contractStartDate, monthEnd
        ));

        response.setMaxWeekKey(timeline.maxWeekKey());
        response.setSelectedLastDataWeekKey(selectedWeekKey);
        response.setPeriods(periods);
        response.setXAxis(periods.stream().map(LongtermAmountPriceTrendDTO::getPeriod).toList());

        ChartSeriesDTO coalAmount = new ChartSeriesDTO();
        coalAmount.setName("煤电量");
        coalAmount.setType("bar");
        coalAmount.setColor("#5470C6");
        coalAmount.setData(periods.stream().map(LongtermAmountPriceTrendDTO::getCoalAmount).toList());

        ChartSeriesDTO windAmount = new ChartSeriesDTO();
        windAmount.setName("风电量");
        windAmount.setType("bar");
        windAmount.setColor("#73C0DE");
        windAmount.setData(periods.stream().map(LongtermAmountPriceTrendDTO::getWindAmount).toList());

        ChartSeriesDTO solarAmount = new ChartSeriesDTO();
        solarAmount.setName("光伏电量");
        solarAmount.setType("bar");
        solarAmount.setColor("#91CC75");
        solarAmount.setData(periods.stream().map(LongtermAmountPriceTrendDTO::getSolarAmount).toList());

        ChartSeriesDTO coalPrice = new ChartSeriesDTO();
        coalPrice.setName("煤电价");
        coalPrice.setType("line");
        coalPrice.setColor("#EE6666");
        coalPrice.setShowSymbol(Boolean.TRUE);
        coalPrice.setSmooth(Boolean.FALSE);
        coalPrice.setShowLabel(Boolean.TRUE);
        coalPrice.setData(periods.stream().map(LongtermAmountPriceTrendDTO::getCoalPrice).toList());

        ChartSeriesDTO windPrice = new ChartSeriesDTO();
        windPrice.setName("风电价");
        windPrice.setType("line");
        windPrice.setColor("#FAC858");
        windPrice.setShowSymbol(Boolean.TRUE);
        windPrice.setSmooth(Boolean.FALSE);
        windPrice.setShowLabel(Boolean.TRUE);
        windPrice.setData(periods.stream().map(LongtermAmountPriceTrendDTO::getWindPrice).toList());

        ChartSeriesDTO solarPrice = new ChartSeriesDTO();
        solarPrice.setName("光伏电价");
        solarPrice.setType("line");
        solarPrice.setColor("#9A60B4");
        solarPrice.setShowSymbol(Boolean.TRUE);
        solarPrice.setSmooth(Boolean.FALSE);
        solarPrice.setShowLabel(Boolean.TRUE);
        solarPrice.setData(periods.stream().map(LongtermAmountPriceTrendDTO::getSolarPrice).toList());

        response.getSeries().add(coalAmount);
        response.getSeries().add(windAmount);
        response.getSeries().add(solarAmount);
        response.getSeries().add(coalPrice);
        response.getSeries().add(windPrice);
        response.getSeries().add(solarPrice);
        return response;
    }

    public RegionalSpotTrendResponseDTO getRegionalSpotTrend(Integer regionId, String lastDataWeekKey) {
        if (regionId == null || regionId < 1 || regionId > 7 || regionId == 8) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid regionId: " + regionId);
        }
        LocalDate latestDate = weeklyReportMapper.getLatestSpotDate();
        RegionalSpotTrendResponseDTO response = new RegionalSpotTrendResponseDTO();
        response.setUnit("元/MWh");
        response.setWeekRule("周六到下周五");
        response.setSeries(new ArrayList<>());
        response.setXAxis(new ArrayList<>());
        response.setCompanies(new ArrayList<>());
        if (latestDate == null) {
            response.setMaxWeekKey(null);
            response.setSelectedLastDataWeekKey(null);
            return response;
        }

        WeekTimeline timeline = buildWeekTimeline(latestDate);
        if (timeline.weekKeys().isEmpty()) {
            response.setMaxWeekKey(null);
            response.setSelectedLastDataWeekKey(null);
            return response;
        }

        String selectedWeekKey = lastDataWeekKey == null || lastDataWeekKey.isBlank()
                ? timeline.maxWeekKey()
                : lastDataWeekKey.trim().toUpperCase(Locale.ROOT);
        WeekWindow selectedWindow = timeline.weekWindowByKey().get(selectedWeekKey);
        if (selectedWindow == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid lastDataWeekKey: " + selectedWeekKey);
        }

        List<RegionalCompanyDailySpotPriceDTO> dailyRows = weeklyReportMapper.getRegionalCompanyDailySpotPriceTrend(
                regionId,
                LocalDate.of(selectedWindow.startDate().getYear(), 1, 1),
                selectedWindow.endDate()
        );
        Map<String, Map<LocalDate, BigDecimal>> companyDailyMap = new LinkedHashMap<>();
        for (RegionalCompanyDailySpotPriceDTO row : dailyRows) {
            if (row.getCompanyName() == null || row.getPriceDate() == null || row.getAvgSpotPrice() == null) {
                continue;
            }
            companyDailyMap.computeIfAbsent(row.getCompanyName(), ignored -> new TreeMap<>())
                    .put(row.getPriceDate(), scalePrice(row.getAvgSpotPrice()));
        }

        List<WeekWindow> windows = timeline.recentWeekWindowsUpTo(selectedWeekKey, RECENT_WEEK_COUNT);
        response.setMaxWeekKey(timeline.maxWeekKey());
        response.setSelectedLastDataWeekKey(selectedWeekKey);
        response.setXAxis(windows.stream().map(WeekWindow::key).toList());
        response.setRegionId(regionId == 7 ? 4 : regionId);
        response.setRegionName(regionName(regionId == 7 ? 4 : regionId));

        List<RegionalSpotTrendCompanyDTO> companies = new ArrayList<>();
        for (Map.Entry<String, Map<LocalDate, BigDecimal>> entry : companyDailyMap.entrySet()) {
            List<RegionalSpotTrendWeekDTO> weeks = new ArrayList<>();
            List<BigDecimal> lineData = new ArrayList<>();
            for (WeekWindow window : windows) {
                RegionalSpotTrendWeekDTO week = buildRegionalWeekStat(window.startDate(), window.endDate(), entry.getValue());
                weeks.add(week);
                lineData.add(week.getAvgSpotPrice());
            }

            RegionalSpotTrendCompanyDTO company = new RegionalSpotTrendCompanyDTO();
            company.setCompanyName(entry.getKey());
            company.setWeeks(weeks);
            companies.add(company);

            ChartSeriesDTO series = new ChartSeriesDTO();
            series.setName(entry.getKey());
            series.setType("line");
            series.setShowSymbol(Boolean.TRUE);
            series.setSmooth(Boolean.FALSE);
            series.setShowLabel(Boolean.TRUE);
            series.setData(lineData);
            response.getSeries().add(series);
        }
        response.setCompanies(companies);
        return response;
    }

    private DateRange resolveRange(Integer year, Integer startMonth, Integer endMonth) {
        int y = year == null ? LocalDate.now().getYear() : year;
        int sm = startMonth == null ? 1 : Math.max(1, Math.min(12, startMonth));
        int em = endMonth == null ? 6 : Math.max(1, Math.min(12, endMonth));
        if (sm > em) {
            int tmp = sm;
            sm = em;
            em = tmp;
        }
        LocalDate start = LocalDate.of(y, sm, 1);
        LocalDate end = LocalDate.of(y, em, 1).withDayOfMonth(LocalDate.of(y, em, 1).lengthOfMonth());
        return new DateRange(start, end);
    }

    private ProvincialSpotTrendWeekDTO buildWeekStat(LocalDate weekStart, LocalDate weekEnd, List<BigDecimal> values) {
        if (values.isEmpty()) {
            ProvincialSpotTrendWeekDTO empty = new ProvincialSpotTrendWeekDTO();
            empty.setWeekStartDate(weekStart);
            empty.setWeekEndDate(weekEnd);
            empty.setMinPrice(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            empty.setMaxPrice(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            empty.setAveragePrice(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            return empty;
        }
        DescriptiveStatistics statistics = new DescriptiveStatistics();
        for (BigDecimal value : values) {
            statistics.addValue(value.doubleValue());
        }

        ProvincialSpotTrendWeekDTO dto = new ProvincialSpotTrendWeekDTO();
        dto.setWeekStartDate(weekStart);
        dto.setWeekEndDate(weekEnd);
        dto.setMinPrice(scalePrice(BigDecimal.valueOf(statistics.getMin())));
        dto.setMaxPrice(scalePrice(BigDecimal.valueOf(statistics.getMax())));
        dto.setAveragePrice(scalePrice(BigDecimal.valueOf(statistics.getMean())));
        return dto;
    }

    private CompanyPriceTrendWeekDTO buildCompanyWeekStat(LocalDate weekStart, LocalDate weekEnd, Map<LocalDate, CompanyDailyPriceDTO> dailyMap) {
        List<BigDecimal> marketValues = new ArrayList<>();
        List<BigDecimal> coalValues = new ArrayList<>();
        List<BigDecimal> windValues = new ArrayList<>();
        List<BigDecimal> solarValues = new ArrayList<>();

        for (Map.Entry<LocalDate, CompanyDailyPriceDTO> entry : dailyMap.entrySet()) {
            LocalDate date = entry.getKey();
            if (date.isBefore(weekStart) || date.isAfter(weekEnd)) {
                continue;
            }
            CompanyDailyPriceDTO row = entry.getValue();
            if (row.getMarketAvgPrice() != null) {
                marketValues.add(scalePrice(row.getMarketAvgPrice()));
            }
            if (row.getCoalChngPrice() != null) {
                coalValues.add(scalePrice(row.getCoalChngPrice()));
            }
            if (row.getWindChngPrice() != null) {
                windValues.add(scalePrice(row.getWindChngPrice()));
            }
            if (row.getSolarChngPrice() != null) {
                solarValues.add(scalePrice(row.getSolarChngPrice()));
            }
        }

        CompanyPriceTrendWeekDTO dto = new CompanyPriceTrendWeekDTO();
        dto.setWeekStartDate(weekStart);
        dto.setWeekEndDate(weekEnd);
        dto.setMarketAvgPrice(calculateAverage(marketValues));
        dto.setCoalChngPrice(calculateAverage(coalValues));
        dto.setWindChngPrice(calculateAverage(windValues));
        dto.setSolarChngPrice(calculateAverage(solarValues));
        return dto;
    }

    private RegionalSpotTrendWeekDTO buildRegionalWeekStat(LocalDate weekStart, LocalDate weekEnd, Map<LocalDate, BigDecimal> dailyMap) {
        List<BigDecimal> values = new ArrayList<>();
        if (dailyMap != null) {
            for (Map.Entry<LocalDate, BigDecimal> entry : dailyMap.entrySet()) {
                LocalDate date = entry.getKey();
                if (!date.isBefore(weekStart) && !date.isAfter(weekEnd) && entry.getValue() != null) {
                    values.add(entry.getValue());
                }
            }
        }
        RegionalSpotTrendWeekDTO dto = new RegionalSpotTrendWeekDTO();
        dto.setWeekStartDate(weekStart);
        dto.setWeekEndDate(weekEnd);
        dto.setAvgSpotPrice(calculateAverage(values));
        return dto;
    }

    private BigDecimal calculateAverage(List<BigDecimal> values) {
        if (values.isEmpty()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (BigDecimal value : values) {
            sum = sum.add(value);
        }
        return scalePrice(sum.divide(BigDecimal.valueOf(values.size()), 10, RoundingMode.HALF_UP));
    }

    private BigDecimal scalePrice(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private List<BigDecimal> repeatValue(BigDecimal value, int size) {
        List<BigDecimal> result = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            result.add(value);
        }
        return result;
    }

    private record DateRange(LocalDate startDate, LocalDate endDate) {
    }

    private Map<LocalDate, List<BigDecimal>> buildDailyMarketPrices(List<ProvincialSpotCompanyDailyDTO> rows) {
        Map<LocalDate, List<BigDecimal>> dailyMarketPrices = new TreeMap<>();
        Map<String, Boolean> excludedNames = new HashMap<>();
        excludedNames.put("天津", Boolean.TRUE);
        excludedNames.put("北京", Boolean.TRUE);
        excludedNames.put("雅江", Boolean.TRUE);
        excludedNames.put("新能源", Boolean.TRUE);

        Map<LocalDate, Map<Long, ProvincialSpotCompanyDailyDTO>> grouped = new TreeMap<>();
        for (ProvincialSpotCompanyDailyDTO row : rows) {
            if (row.getPriceDate() == null || row.getCompanyId() == null) {
                continue;
            }
            grouped.computeIfAbsent(row.getPriceDate(), ignored -> new LinkedHashMap<>())
                    .put(row.getCompanyId(), row);
        }

        for (Map.Entry<LocalDate, Map<Long, ProvincialSpotCompanyDailyDTO>> entry : grouped.entrySet()) {
            List<BigDecimal> prices = new ArrayList<>();
            for (ProvincialSpotCompanyDailyDTO row : entry.getValue().values()) {
                if (row.getCompanyName() != null && excludedNames.containsKey(row.getCompanyName())) {
                    continue;
                }
                BigDecimal price = row.getCoalSpotAvgPrice();
                if (price == null) {
                    price = row.getWindSpotAvgPrice();
                }
                if (price != null) {
                    prices.add(scalePrice(price));
                }
            }
            if (!prices.isEmpty()) {
                dailyMarketPrices.put(entry.getKey(), prices);
            }
        }
        return dailyMarketPrices;
    }

    private List<BigDecimal> collectDailyValues(Map<LocalDate, List<BigDecimal>> dailyMarketPrices, LocalDate startDate, LocalDate endDate) {
        List<BigDecimal> values = new ArrayList<>();
        for (Map.Entry<LocalDate, List<BigDecimal>> entry : dailyMarketPrices.entrySet()) {
            LocalDate date = entry.getKey();
            if (!date.isBefore(startDate) && !date.isAfter(endDate) && !entry.getValue().isEmpty()) {
                values.add(calculateAverage(entry.getValue()));
            }
        }
        return values;
    }

    private String regionName(Integer regionId) {
        return switch (regionId) {
            case 1 -> "东北";
            case 2 -> "西北";
            case 3 -> "华北";
            case 4 -> "华中";
            case 5 -> "华东";
            case 6 -> "南方";
            default -> "未知区域";
        };
    }

    private WeekTimeline buildWeekTimeline(LocalDate latestDate) {
        LocalDate firstWeekStart = firstWeekStartOfYear(latestDate.getYear());
        LocalDate latestWeekStart = latestDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.SATURDAY));
        LocalDate maxCompleteWeekStart = latestDate.getDayOfWeek() == DayOfWeek.FRIDAY
                ? latestWeekStart
                : latestWeekStart.minusWeeks(1);
        if (maxCompleteWeekStart.isBefore(firstWeekStart)) {
            return new WeekTimeline(new ArrayList<>(), new LinkedHashMap<>(), null);
        }

        List<String> weekKeys = new ArrayList<>();
        Map<String, WeekWindow> windows = new LinkedHashMap<>();
        int weekNumber = 1;
        for (LocalDate start = firstWeekStart; !start.isAfter(maxCompleteWeekStart); start = start.plusWeeks(1), weekNumber++) {
            WeekWindow window = new WeekWindow(latestDate.getYear(), weekNumber, start, start.plusDays(6));
            weekKeys.add(window.key());
            windows.put(window.key(), window);
        }
        return new WeekTimeline(weekKeys, windows, weekKeys.isEmpty() ? null : weekKeys.get(weekKeys.size() - 1));
    }

    private LocalDate firstWeekStartOfYear(int year) {
        LocalDate firstDay = LocalDate.of(year, 1, 1);
        return firstDay.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY));
    }

    private record WeekTimeline(List<String> weekKeys, Map<String, WeekWindow> weekWindowByKey, String maxWeekKey) {
        private List<WeekWindow> recentWeekWindowsUpTo(String selectedWeekKey, int limit) {
            int selectedIndex = weekKeys.indexOf(selectedWeekKey);
            if (selectedIndex < 0) {
                return new ArrayList<>();
            }
            int fromIndex = Math.max(0, selectedIndex - limit + 1);
            List<WeekWindow> windows = new ArrayList<>();
            for (int i = fromIndex; i <= selectedIndex; i++) {
                windows.add(weekWindowByKey.get(weekKeys.get(i)));
            }
            return windows;
        }
    }

    private record WeekWindow(int year, int weekNumber, LocalDate startDate, LocalDate endDate) {
        private String key() {
            return year + "W" + weekNumber;
        }
    }
}
