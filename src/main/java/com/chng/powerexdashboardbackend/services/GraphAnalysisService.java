package com.chng.powerexdashboardbackend.services;

import com.chng.powerexdashboardbackend.dto.graphanalysis.SpotAnalysisDailyPriceDTO;
import com.chng.powerexdashboardbackend.dto.graphanalysis.SpotDateRangeDTO;
import com.chng.powerexdashboardbackend.dto.weeklyreport.ChartSeriesDTO;
import com.chng.powerexdashboardbackend.dto.weeklyreport.LongtermAmountPriceTrendDTO;
import com.chng.powerexdashboardbackend.enums.RegionEnum;
import com.chng.powerexdashboardbackend.mapper.graphanalysis.LongtermAnalysisMapper;
import com.chng.powerexdashboardbackend.mapper.graphanalysis.SpotAnalysisMapper;
import com.chng.powerexdashboardbackend.responses.graphanalysis.GraphOptionItem;
import com.chng.powerexdashboardbackend.responses.graphanalysis.LongtermAnalysisOptionsResponse;
import com.chng.powerexdashboardbackend.responses.graphanalysis.LongtermAnalysisTrendResponse;
import com.chng.powerexdashboardbackend.responses.graphanalysis.SpotAnalysisOptionsResponse;
import com.chng.powerexdashboardbackend.responses.graphanalysis.SpotAnalysisPointDTO;
import com.chng.powerexdashboardbackend.responses.graphanalysis.SpotAnalysisTrendResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class GraphAnalysisService {

    private static final String FILTER_TYPE_COMPANY = "company";
    private static final String FILTER_TYPE_REGION = "region";
    private static final String TIME_SCALE_DAY = "day";
    private static final String TIME_SCALE_WEEK = "week";
    private static final String TIME_SCALE_TEN_DAY = "ten-day";
    private static final String TIME_SCALE_MONTH = "month";
    private static final int DEFAULT_WEEK_WINDOW_SIZE = 10;
    private static final List<Integer> LONGTERM_GEN_TYPES = List.of(1, 2, 3);
    private static final int COAL_GEN_TYPE = 1;
    private static final int WIND_GEN_TYPE = 3;
    private static final int SOLAR_GEN_TYPE = 2;

    private final SpotAnalysisMapper spotAnalysisMapper;
    private final LongtermAnalysisMapper longtermAnalysisMapper;

    public SpotAnalysisOptionsResponse getSpotAnalysisOptions() {
        SpotDateRangeDTO dateRange = spotAnalysisMapper.selectSpotDateRange();
        LocalDate minDate = dateRange == null ? null : dateRange.getMinDate();
        LocalDate maxDate = dateRange == null ? null : dateRange.getMaxDate();
        DateRange defaultRange = resolveDefaultRange(minDate, maxDate);

        SpotAnalysisOptionsResponse response = new SpotAnalysisOptionsResponse();
        response.setChartCode("SpotAnalysis");
        response.setChartName("现货实时均价走势");
        response.setWeekRule("周六到下周五");
        response.setDefaultFilterType(FILTER_TYPE_COMPANY);
        response.setDefaultTimeScale(TIME_SCALE_WEEK);
        response.setMinDate(minDate);
        response.setMaxDate(maxDate);
        response.setDefaultStartDate(defaultRange.startDate());
        response.setDefaultEndDate(defaultRange.endDate());
        response.setFilterTypeOptions(List.of(
                new GraphOptionItem(FILTER_TYPE_COMPANY, "公司"),
                new GraphOptionItem(FILTER_TYPE_REGION, "地区")
        ));
        response.setTimeScaleOptions(List.of(
                new GraphOptionItem(TIME_SCALE_DAY, "日"),
                new GraphOptionItem(TIME_SCALE_WEEK, "周"),
                new GraphOptionItem(TIME_SCALE_TEN_DAY, "旬"),
                new GraphOptionItem(TIME_SCALE_MONTH, "月")
        ));
        response.setCompanyOptions(
                spotAnalysisMapper.selectCompanyOptions().stream()
                        .map(item -> new GraphOptionItem(String.valueOf(item.getId()), item.getName()))
                        .toList()
        );
        response.setRegionOptions(
                spotAnalysisMapper.selectRegionIds().stream()
                        .map(this::toRegionOption)
                        .filter(Objects::nonNull)
                        .toList()
        );
        return response;
    }

    public SpotAnalysisTrendResponse getSpotAnalysisTrend(String filterType,
                                                          List<Integer> filterIds,
                                                          String timeScale,
                                                          LocalDate startDate,
                                                          LocalDate endDate) {
        String normalizedFilterType = normalizeFilterType(filterType);
        String normalizedTimeScale = normalizeTimeScale(timeScale);

        SpotDateRangeDTO dateRange = spotAnalysisMapper.selectSpotDateRange();
        LocalDate minDate = dateRange == null ? null : dateRange.getMinDate();
        LocalDate maxDate = dateRange == null ? null : dateRange.getMaxDate();
        DateRange defaultRange = resolveDefaultRange(minDate, maxDate);
        DateRange queryRange = resolveQueryRange(startDate, endDate, defaultRange.startDate(), defaultRange.endDate());

        List<SpotAnalysisDailyPriceDTO> dailyRows = spotAnalysisMapper.selectDailyAvgSpotPrice(
                normalizedFilterType,
                filterIds == null ? List.of() : filterIds,
                queryRange.startDate(),
                queryRange.endDate()
        );
        List<SpotAnalysisPointDTO> points = aggregateByTimeScale(dailyRows, normalizedTimeScale);

        SpotAnalysisTrendResponse response = new SpotAnalysisTrendResponse();
        response.setChartCode("SpotAnalysis");
        response.setChartName("现货实时均价走势");
        response.setUnit("元/MWh");
        response.setWeekRule("周六到下周五");
        response.setFilterType(normalizedFilterType);
        response.setFilterIds(filterIds == null ? List.of() : filterIds);
        response.setTimeScale(normalizedTimeScale);
        response.setStartDate(queryRange.startDate());
        response.setEndDate(queryRange.endDate());
        response.setPoints(points);
        response.setXAxis(points.stream().map(SpotAnalysisPointDTO::getPeriodLabel).toList());

        ChartSeriesDTO minSeries = new ChartSeriesDTO();
        minSeries.setName("最小值");
        minSeries.setType("line");
        minSeries.setColor("#73C0DE");
        minSeries.setShowSymbol(Boolean.TRUE);
        minSeries.setSmooth(Boolean.FALSE);
        minSeries.setShowLabel(Boolean.TRUE);
        minSeries.setData(points.stream().map(SpotAnalysisPointDTO::getMinSpotPrice).toList());

        ChartSeriesDTO maxSeries = new ChartSeriesDTO();
        maxSeries.setName("最大值");
        maxSeries.setType("line");
        maxSeries.setColor("#FAC858");
        maxSeries.setShowSymbol(Boolean.TRUE);
        maxSeries.setSmooth(Boolean.FALSE);
        maxSeries.setShowLabel(Boolean.TRUE);
        maxSeries.setData(points.stream().map(SpotAnalysisPointDTO::getMaxSpotPrice).toList());

        ChartSeriesDTO avgSeries = new ChartSeriesDTO();
        avgSeries.setName("现货实时均价");
        avgSeries.setType("line");
        avgSeries.setColor("#5470C6");
        avgSeries.setShowSymbol(Boolean.TRUE);
        avgSeries.setSmooth(Boolean.FALSE);
        avgSeries.setShowLabel(Boolean.TRUE);
        avgSeries.setData(points.stream().map(SpotAnalysisPointDTO::getAvgSpotPrice).toList());
        response.setSeries(List.of(minSeries, maxSeries, avgSeries));
        return response;
    }

    public LongtermAnalysisOptionsResponse getLongtermAnalysisOptions() {
        List<String> monthOptions = longtermAnalysisMapper.selectLongtermMonthOptions();

        LongtermAnalysisOptionsResponse response = new LongtermAnalysisOptionsResponse();
        response.setChartCode("LongtermAnalysis");
        response.setChartName("中长期交易量价走势");
        response.setDefaultFilterType(FILTER_TYPE_COMPANY);
        response.setDefaultMonth(monthOptions.isEmpty() ? null : monthOptions.getLast());
        response.setFilterTypeOptions(List.of(
                new GraphOptionItem(FILTER_TYPE_COMPANY, "公司"),
                new GraphOptionItem(FILTER_TYPE_REGION, "地区")
        ));
        response.setCompanyOptions(
                spotAnalysisMapper.selectCompanyOptions().stream()
                        .map(item -> new GraphOptionItem(String.valueOf(item.getId()), item.getName()))
                        .toList()
        );
        response.setRegionOptions(
                spotAnalysisMapper.selectRegionIds().stream()
                        .map(this::toRegionOption)
                        .filter(Objects::nonNull)
                        .toList()
        );
        response.setMonthOptions(monthOptions.stream()
                .map(month -> new GraphOptionItem(month, month))
                .toList());
        return response;
    }

    public LongtermAnalysisTrendResponse getLongtermAnalysisTrend(String filterType,
                                                                  List<Integer> filterIds,
                                                                  String month) {
        String normalizedFilterType = normalizeFilterType(filterType);
        List<String> monthOptions = longtermAnalysisMapper.selectLongtermMonthOptions();
        String selectedMonth = resolveSelectedMonth(month, monthOptions);

        LongtermAnalysisTrendResponse response = new LongtermAnalysisTrendResponse();
        response.setChartCode("LongtermAnalysis");
        response.setChartName("中长期交易量价走势");
        response.setUnitAmount("亿千瓦时");
        response.setUnitPrice("元/MWh");
        response.setFilterType(normalizedFilterType);
        response.setFilterIds(filterIds == null ? List.of() : filterIds);
        response.setSelectedMonth(selectedMonth);
        response.setXAxis(new ArrayList<>());
        response.setSeries(new ArrayList<>());
        response.setPeriods(new ArrayList<>());

        if (selectedMonth == null) {
            return response;
        }

        YearMonth yearMonth;
        try {
            yearMonth = YearMonth.parse(selectedMonth);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid month: " + selectedMonth);
        }

        LocalDate contractStartDate = LocalDate.of(yearMonth.getYear(), 1, 1);
        LocalDate contractEndDate = yearMonth.atEndOfMonth();
        LocalDate yearEndDate = LocalDate.of(yearMonth.getYear(), 12, 31);

        List<Integer> normalizedFilterIds = filterIds == null ? List.of() : filterIds;
        List<LongtermAmountPriceTrendDTO> periods = new ArrayList<>();
        LongtermAmountPriceTrendDTO annual = longtermAnalysisMapper.selectLongtermAnnualTrend(
                normalizedFilterType,
                normalizedFilterIds,
                contractStartDate,
                yearEndDate
        );
        if (annual != null) {
            periods.add(annual);
        }
        periods.addAll(longtermAnalysisMapper.selectLongtermMonthlyTrend(
                normalizedFilterType,
                normalizedFilterIds,
                contractStartDate,
                contractEndDate
        ));

        response.setPeriods(periods);
        response.setXAxis(periods.stream().map(LongtermAmountPriceTrendDTO::getPeriod).toList());
        response.setSeries(buildLongtermSeries(periods));
        return response;
    }

    private List<SpotAnalysisPointDTO> aggregateByTimeScale(List<SpotAnalysisDailyPriceDTO> dailyRows, String timeScale) {
        if (dailyRows == null || dailyRows.isEmpty()) {
            return List.of();
        }
        Map<String, AggregateBucket> bucketMap = new LinkedHashMap<>();
        for (SpotAnalysisDailyPriceDTO row : dailyRows) {
            if (row.getPriceDate() == null || row.getAvgSpotPrice() == null) {
                continue;
            }
            PeriodSlot slot = resolveSlot(row.getPriceDate(), timeScale);
            AggregateBucket bucket = bucketMap.computeIfAbsent(
                    slot.key(),
                    ignored -> new AggregateBucket(slot.key(), slot.label(), slot.startDate(), slot.endDate())
            );
            bucket.sum = bucket.sum.add(row.getAvgSpotPrice());
            bucket.count++;
            bucket.values.add(row.getAvgSpotPrice());
        }

        List<AggregateBucket> buckets = new ArrayList<>(bucketMap.values());
        buckets.sort((a, b) -> a.startDate.compareTo(b.startDate));

        List<SpotAnalysisPointDTO> points = new ArrayList<>(buckets.size());
        for (AggregateBucket bucket : buckets) {
            SpotAnalysisPointDTO point = new SpotAnalysisPointDTO();
            point.setPeriodKey(bucket.key);
            point.setPeriodLabel(bucket.label);
            point.setPeriodStartDate(bucket.startDate);
            point.setPeriodEndDate(bucket.endDate);
            point.setMinSpotPrice(minValue(bucket.values));
            point.setMaxSpotPrice(maxValue(bucket.values));
            point.setAvgSpotPrice(ratio(bucket.sum, BigDecimal.valueOf(bucket.count)));
            points.add(point);
        }
        return points;
    }

    private PeriodSlot resolveSlot(LocalDate date, String timeScale) {
        return switch (timeScale) {
            case TIME_SCALE_DAY -> new PeriodSlot(
                    "D-" + date,
                    date.toString(),
                    date,
                    date
            );
            case TIME_SCALE_WEEK -> {
                LocalDate start = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.SATURDAY));
                LocalDate end = start.plusDays(6);
                yield new PeriodSlot(
                        "W-" + start,
                        start + "~" + end,
                        start,
                        end
                );
            }
            case TIME_SCALE_TEN_DAY -> {
                int day = date.getDayOfMonth();
                YearMonth ym = YearMonth.from(date);
                int periodNo = day <= 10 ? 1 : (day <= 20 ? 2 : 3);
                LocalDate start = switch (periodNo) {
                    case 1 -> date.withDayOfMonth(1);
                    case 2 -> date.withDayOfMonth(11);
                    default -> date.withDayOfMonth(21);
                };
                LocalDate end = switch (periodNo) {
                    case 1 -> date.withDayOfMonth(10);
                    case 2 -> date.withDayOfMonth(20);
                    default -> ym.atEndOfMonth();
                };
                String labelSuffix = switch (periodNo) {
                    case 1 -> "上旬";
                    case 2 -> "中旬";
                    default -> "下旬";
                };
                yield new PeriodSlot(
                        "T-" + date.getYear() + "-" + date.getMonthValue() + "-" + periodNo,
                        ym + labelSuffix,
                        start,
                        end
                );
            }
            case TIME_SCALE_MONTH -> {
                YearMonth ym = YearMonth.from(date);
                yield new PeriodSlot(
                        "M-" + ym,
                        ym.toString(),
                        ym.atDay(1),
                        ym.atEndOfMonth()
                );
            }
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid timeScale: " + timeScale);
        };
    }

    private DateRange resolveQueryRange(LocalDate startDate,
                                        LocalDate endDate,
                                        LocalDate defaultStartDate,
                                        LocalDate defaultEndDate) {
        LocalDate resolvedStart = startDate == null ? defaultStartDate : startDate;
        LocalDate resolvedEnd = endDate == null ? defaultEndDate : endDate;
        if (resolvedStart == null || resolvedEnd == null) {
            return new DateRange(null, null);
        }
        if (resolvedStart.isAfter(resolvedEnd)) {
            LocalDate temp = resolvedStart;
            resolvedStart = resolvedEnd;
            resolvedEnd = temp;
        }
        return new DateRange(resolvedStart, resolvedEnd);
    }

    private DateRange resolveDefaultRange(LocalDate minDate, LocalDate maxDate) {
        if (minDate == null || maxDate == null) {
            return new DateRange(null, null);
        }
        LocalDate defaultEnd = maxDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.FRIDAY));
        if (defaultEnd.isBefore(minDate)) {
            defaultEnd = maxDate;
        }
        LocalDate defaultStart = defaultEnd.minusWeeks(DEFAULT_WEEK_WINDOW_SIZE - 1)
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.SATURDAY));
        if (defaultStart.isBefore(minDate)) {
            defaultStart = minDate;
        }
        return new DateRange(defaultStart, defaultEnd);
    }

    private String normalizeFilterType(String filterType) {
        String normalized = filterType == null ? FILTER_TYPE_COMPANY : filterType.trim().toLowerCase(Locale.ROOT);
        if (!FILTER_TYPE_COMPANY.equals(normalized) && !FILTER_TYPE_REGION.equals(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid filterType: " + filterType);
        }
        return normalized;
    }

    private String normalizeTimeScale(String timeScale) {
        String normalized = timeScale == null ? TIME_SCALE_WEEK : timeScale.trim().toLowerCase(Locale.ROOT);
        if (!List.of(TIME_SCALE_DAY, TIME_SCALE_WEEK, TIME_SCALE_TEN_DAY, TIME_SCALE_MONTH).contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid timeScale: " + timeScale);
        }
        return normalized;
    }

    private String resolveSelectedMonth(String month, List<String> monthOptions) {
        if (monthOptions == null || monthOptions.isEmpty()) {
            return null;
        }
        String selected = month == null || month.isBlank() ? monthOptions.getLast() : month.trim();
        if (!monthOptions.contains(selected)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid month: " + selected);
        }
        return selected;
    }

    private List<ChartSeriesDTO> buildLongtermSeries(List<LongtermAmountPriceTrendDTO> periods) {
        List<ChartSeriesDTO> series = new ArrayList<>();
        for (Integer genType : LONGTERM_GEN_TYPES) {
            String name = switch (genType) {
                case COAL_GEN_TYPE -> "煤";
                case WIND_GEN_TYPE -> "风";
                case SOLAR_GEN_TYPE -> "光";
                default -> "";
            };
            String color = switch (genType) {
                case COAL_GEN_TYPE -> "#5470C6";
                case WIND_GEN_TYPE -> "#73C0DE";
                case SOLAR_GEN_TYPE -> "#91CC75";
                default -> "#5470C6";
            };
            String priceColor = switch (genType) {
                case COAL_GEN_TYPE -> "#EE6666";
                case WIND_GEN_TYPE -> "#FAC858";
                case SOLAR_GEN_TYPE -> "#9A60B4";
                default -> "#EE6666";
            };

            ChartSeriesDTO amountSeries = new ChartSeriesDTO();
            amountSeries.setName(name + "电量");
            amountSeries.setType("bar");
            amountSeries.setColor(color);
            amountSeries.setData(periods.stream()
                    .map(period -> switch (genType) {
                        case COAL_GEN_TYPE -> period.getCoalAmount();
                        case WIND_GEN_TYPE -> period.getWindAmount();
                        case SOLAR_GEN_TYPE -> period.getSolarAmount();
                        default -> BigDecimal.ZERO;
                    })
                    .toList());

            ChartSeriesDTO priceSeries = new ChartSeriesDTO();
            priceSeries.setName(name + "电价");
            priceSeries.setType("line");
            priceSeries.setColor(priceColor);
            priceSeries.setShowSymbol(Boolean.TRUE);
            priceSeries.setSmooth(Boolean.FALSE);
            priceSeries.setShowLabel(Boolean.TRUE);
            priceSeries.setData(periods.stream()
                    .map(period -> switch (genType) {
                        case COAL_GEN_TYPE -> period.getCoalPrice();
                        case WIND_GEN_TYPE -> period.getWindPrice();
                        case SOLAR_GEN_TYPE -> period.getSolarPrice();
                        default -> BigDecimal.ZERO;
                    })
                    .toList());
            series.add(amountSeries);
            series.add(priceSeries);
        }
        return series;
    }

    private GraphOptionItem toRegionOption(Integer regionId) {
        if (regionId == null) {
            return null;
        }
        try {
            RegionEnum region = RegionEnum.of(regionId);
            return new GraphOptionItem(String.valueOf(region.getId()), region.getName());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private BigDecimal ratio(BigDecimal numerator, BigDecimal denominator) {
        if (denominator == null || denominator.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return numerator.divide(denominator, 10, RoundingMode.HALF_UP).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal minValue(List<BigDecimal> values) {
        if (values == null || values.isEmpty()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal min = values.getFirst();
        for (BigDecimal value : values) {
            if (value.compareTo(min) < 0) {
                min = value;
            }
        }
        return min.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal maxValue(List<BigDecimal> values) {
        if (values == null || values.isEmpty()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal max = values.getFirst();
        for (BigDecimal value : values) {
            if (value.compareTo(max) > 0) {
                max = value;
            }
        }
        return max.setScale(2, RoundingMode.HALF_UP);
    }

    private record DateRange(LocalDate startDate, LocalDate endDate) {
    }

    private record PeriodSlot(String key, String label, LocalDate startDate, LocalDate endDate) {
    }

    private static final class AggregateBucket {
        private final String key;
        private final String label;
        private final LocalDate startDate;
        private final LocalDate endDate;
        private BigDecimal sum = BigDecimal.ZERO;
        private int count = 0;
        private final List<BigDecimal> values = new ArrayList<>();

        private AggregateBucket(String key, String label, LocalDate startDate, LocalDate endDate) {
            this.key = key;
            this.label = label;
            this.startDate = startDate;
            this.endDate = endDate;
        }
    }
}
