# 图表分析接口文档（中文）

基线路径：`/api/graph-analysis`

## 概述
- 当前先提供 `GraphAnalysis` 页第一张图：`SpotAnalysis`（现货实时均价走势）。
- 支持三行筛选：
  - 第一行：筛选类型（公司/地区）+ 对应右侧下拉项（公司列表或地区列表）
  - 第二行：时间尺度（默认周，可选日/旬/月）
  - 第三行：时间范围（开始日期、结束日期）
- 周口径固定：`周六到下周五`。

---

## 1) 查询 SpotAnalysis 筛选配置

`GET /spot-analysis/options`

### 接口用处
返回前端渲染筛选区所需的全部选项和默认值。

### 请求参数
无。

### 返回字段
- `chartCode`：固定 `SpotAnalysis`
- `chartName`：固定 `现货实时均价走势`
- `weekRule`：固定 `周六到下周五`
- `defaultFilterType`：默认筛选类型（`company`）
- `defaultTimeScale`：默认时间尺度（`week`）
- `minDate` / `maxDate`：可选时间范围边界（来自现货数据）
- `defaultStartDate` / `defaultEndDate`：默认时间范围（默认最近10个完整周窗口）
- `filterTypeOptions`：筛选类型选项（公司/地区）
- `companyOptions`：公司下拉选项（`value=id`，`label=name`）
- `regionOptions`：地区下拉选项（`value=regionId`，`label=地区名`）
- `timeScaleOptions`：时间尺度选项（`day/week/ten-day/month`）

### 示例返回
```json
{
  "chartCode": "SpotAnalysis",
  "chartName": "现货实时均价走势",
  "weekRule": "周六到下周五",
  "defaultFilterType": "company",
  "defaultTimeScale": "week",
  "minDate": "2026-01-01",
  "maxDate": "2026-07-21",
  "defaultStartDate": "2026-05-16",
  "defaultEndDate": "2026-07-17",
  "filterTypeOptions": [
    { "value": "company", "label": "公司" },
    { "value": "region", "label": "地区" }
  ],
  "companyOptions": [
    { "value": "1", "label": "黑龙江" }
  ],
  "regionOptions": [
    { "value": "1", "label": "东北" }
  ],
  "timeScaleOptions": [
    { "value": "day", "label": "日" },
    { "value": "week", "label": "周" },
    { "value": "ten-day", "label": "旬" },
    { "value": "month", "label": "月" }
  ]
}
```

---

## 2) 查询 SpotAnalysis 趋势数据

`GET /spot-analysis/trend`

### 接口用处
按筛选条件返回“现货实时均价走势”可直接绘图的数据结构（`xAxis + series + points`），其中 `series` 含箱线图数据。

### Query 参数
- `filterType`（可选，默认 `company`）
  - `company`：右侧 `filterIds` 按公司 ID 过滤
  - `region`：右侧 `filterIds` 按地区 ID 过滤
- `filterIds`（可选，可多值）
  - 例如：`filterIds=1&filterIds=2`
  - 不传表示该维度不过滤
- `timeScale`（可选，默认 `week`）
  - `day`：按日
  - `week`：按周（周六~周五）
  - `ten-day`：按旬（上旬1-10、中旬11-20、下旬21-月末）
  - `month`：按月
- `startDate`（可选，`yyyy-MM-dd`）
- `endDate`（可选，`yyyy-MM-dd`）

> `startDate/endDate` 任一缺失时，会自动使用 `/options` 返回的默认时间范围补齐。

### 示例请求
```bash
curl 'http://localhost:8080/api/graph-analysis/spot-analysis/trend?filterType=region&filterIds=1&timeScale=week&startDate=2026-05-01&endDate=2026-07-20'
```

### 返回字段
- `chartCode`：固定 `SpotAnalysis`
- `chartName`：固定 `现货实时均价走势`
- `unit`：固定 `元/MWh`
- `weekRule`：固定 `周六到下周五`
- `filterType` / `filterIds` / `timeScale`：本次生效筛选
- `startDate` / `endDate`：本次生效时间范围
- `xAxis`：横轴标签
- `series`：返回3条折线序列（最小值、最大值、现货实时均价）
- `points`：逐点明细（周期key/label、周期起止、最小值/最大值/均值）

### 示例返回
```json
{
  "chartCode": "SpotAnalysis",
  "chartName": "现货实时均价走势",
  "unit": "元/MWh",
  "weekRule": "周六到下周五",
  "filterType": "region",
  "filterIds": [1],
  "timeScale": "week",
  "startDate": "2026-05-01",
  "endDate": "2026-07-20",
  "xAxis": ["2026-05-02~2026-05-08", "2026-05-09~2026-05-15"],
  "series": [
    {
      "name": "最小值",
      "type": "line",
      "color": "#73C0DE",
      "showSymbol": true,
      "smooth": false,
      "showLabel": true,
      "data": [301.2, 299.4]
    },
    {
      "name": "最大值",
      "type": "line",
      "color": "#FAC858",
      "showSymbol": true,
      "smooth": false,
      "showLabel": true,
      "data": [322.9, 315.2]
    },
    {
      "name": "现货实时均价",
      "type": "line",
      "color": "#5470C6",
      "showSymbol": true,
      "smooth": false,
      "showLabel": true,
      "data": [312.35, 308.47]
    }
  ],
  "points": [
    {
      "periodKey": "W-2026-05-02",
      "periodLabel": "2026-05-02~2026-05-08",
      "periodStartDate": "2026-05-02",
      "periodEndDate": "2026-05-08",
      "minSpotPrice": 301.2,
      "maxSpotPrice": 322.9,
      "avgSpotPrice": 312.35
    }
  ]
}
```

---

## 错误码说明
- `400 Bad Request`
  - `filterType` 非法
  - `timeScale` 非法
  - 日期格式不合法

---

## 3) 查询 LongtermAnalysis 筛选配置

`GET /longterm-analysis/options`

### 接口用处
返回“中长期交易量价走势（LongtermAnalysis）”筛选区选项：
- 筛选类型（公司/地区）
- 对应公司列表、地区列表
- 月份列表（`yyyy-MM`）

### 返回字段
- `chartCode`：固定 `LongtermAnalysis`
- `chartName`：固定 `中长期交易量价走势`
- `defaultFilterType`：默认 `company`
- `defaultMonth`：默认月份（月份列表最后一个，即最新月份）
- `filterTypeOptions`：公司/地区
- `companyOptions`：公司下拉
- `regionOptions`：地区下拉
- `monthOptions`：月份下拉

---

## 4) 查询 LongtermAnalysis 趋势数据

`GET /longterm-analysis/trend`

### Query 参数
- `filterType`（可选，默认 `company`）
  - `company`：`filterIds` 按公司 ID 过滤
  - `region`：`filterIds` 按地区 ID 过滤
- `filterIds`（可选，可多值）
- `month`（可选，格式 `yyyy-MM`，默认最新月份）

### 数据口径
- 图形与 weekly 的“中长期交易量价走势”一致：柱线混合图（煤/风/光电量柱 + 煤/风/光电价线）。
- 年度口径：
  - `transaction_period_id = 1`
  - 合同周期按整年重叠：`contract_start_date <= 当年12-31` 且 `contract_end_date >= 当年01-01`
- 月度口径：
  - `transaction_period_id IN (4, 5)`
  - 统计 `当年01月` 到 `所选month` 的月度数据
- 固定筛选：
  - `transaction_type_id IN (1, 3)`
  - `gen_type_id IN (1, 2, 3)`
- 公司/地区筛选通过 `filterType + filterIds` 动态应用。

### 返回字段
- `chartCode`：`LongtermAnalysis`
- `chartName`：`中长期交易量价走势`
- `unitAmount`：`亿千瓦时`
- `unitPrice`：`元/MWh`
- `filterType` / `filterIds` / `selectedMonth`：本次生效筛选
- `xAxis`：`年度 + 1~x月`
- `series`：6条序列（煤/风/光电量柱 + 煤/风/光电价线）
- `periods`：每个周期明细（`period, coalAmount, windAmount, solarAmount, coalPrice, windPrice, solarPrice`）

### 示例请求
```bash
curl 'http://localhost:8080/api/graph-analysis/longterm-analysis/trend?filterType=region&filterIds=1&month=2026-06'
```
