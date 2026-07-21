# 周报用图接口文档（中文）

基线路径：`/api/weekly-report`

## 概述
- 本组接口用于周报图表数据，不负责图表渲染。
- 时间参数统一按 `lastDataWeekKey`（如 `2026W25`，可选）。
- 周维度当前口径：`weekStartDate = 周六`，`weekEndDate = 周五`（由 SQL 周聚合表达式计算）。

---

## 1) 省级现货实时均价走势

### 1.1 可用周选项

`GET /provincial-spot-trend/options`

### 返回
`ProvincialSpotTrendOptionsDTO`

字段：
- `maxWeekKey`：最大可用周，如 `2026W22`
- `weekOptions`：可选周列表，如 `2026W1 ~ 2026W22`

### 1.2 趋势数据

`GET /provincial-spot-trend`

### Query 参数
- `regionId`：区域ID（必填），见下方“regionId 取值（区域）”
- `lastDataWeekKey`：最后数据周数，可为空；为空时默认取最大可用周

### 返回
`ProvincialSpotTrendResponseDTO`

字段：
- `unit`：单位，固定为 `元/MWh`
- `weekRule`：周规则，固定为 `周六到下周五`
- `maxWeekKey`：当前数据可返回的最大周数
- `selectedLastDataWeekKey`：本次请求使用的最后数据周数
- `xAxis`：最近 10 周的周标签数组
- `series[]`：
  - `line`：每周最小值
  - `line`：每周最大值
  - `line`：每周平均价格
  - `line`：年累计实时均价（红色虚线）
- `annualCumulativeMarketAvgPrice`：年累计实时均价详细值
- `weeks[]`：最近 10 周的每周详细统计

> 价格指标均按“市场均价”口径计算：有煤电现货数据的公司取统一结算点实时均价；没有煤电现货数据且不属于天津/北京/雅江/新能源的公司，取风电现货数据的统一结算点实时均价；最后对所有纳入公司做算术平均。

---

## 2) 公司日清分电能量电价走势

`GET /company-price-trend`

### Query 参数
- `lastDataWeekKey`：最后数据周数，可为空；为空时默认取最大可用周

### 返回
`CompanyPriceTrendResponseDTO`

字段：
- `unit`：单位，固定为 `元/MWh`
- `weekRule`：周规则，固定为 `周六到下周五`
- `maxWeekKey`：当前数据可返回的最大周数
- `selectedLastDataWeekKey`：本次请求使用的最后数据周数
- `xAxis`：最近 10 周的周标签数组
- `series[]`：
  - `line`：现货实时价（市场均价）
  - `line`：煤电清分价
  - `line`：风电清分价
  - `line`：光伏清分价
- `weeks[]`：每周明细（`marketAvgPrice`, `coalChngPrice`, `windChngPrice`, `solarChngPrice`）

> 周粒度计算口径：
> - `marketAvgPrice`：先按“每日市场均价”口径得到每日值（有煤取煤，否则取风；排除天津/北京/雅江/新能源，且按公司算术平均），再按“纳入公司数”做周加权汇总。
> - `coalChngPrice`：周内 `SUM(上网电量 * 日清分电价) / SUM(上网电量)`（仅煤电）。
> - `windChngPrice`：周内 `SUM(上网电量 * 日清分电价) / SUM(上网电量)`（仅风电）。
> - `solarChngPrice`：周内 `SUM(上网电量 * 日清分电价) / SUM(上网电量)`（仅光伏）。

---

## 3) 中长期交易量价走势

`GET /longterm-amount-price-trend`

### Query 参数
- `lastDataWeekKey`：最后数据周数，可为空；为空时默认取最大可用周

### 返回
`LongtermAmountPriceTrendResponseDTO`

字段：
- `unitAmount`：电量单位，固定 `亿千瓦时`
- `unitPrice`：电价单位，固定 `元/MWh`
- `maxWeekKey`：当前数据可返回的最大周数
- `selectedLastDataWeekKey`：本次请求使用的最后数据周数
- `xAxis`：`年度` + `YYYY-MM` 时间轴（1~x月）
- `series[]`：6条序列（煤/风/光电量柱 + 煤/风/光电价线）
- `periods[]`：每个时间点明细（`period, coalAmount, windAmount, solarAmount, coalPrice, windPrice, solarPrice`）

> 3.3 口径固定：`gen_type_id IN (1,2,3)`、`transaction_type_id IN (1,3)`；年度仅 `transaction_period_id=1`，月度仅 `transaction_period_id IN (4,5)`。

---

## 4) 区域现货实时均价走势

`GET /regional-spot-trend`

### Query 参数
- `lastDataWeekKey`：最后数据周数，可为空；为空时默认取最大可用周

### 返回
`RegionalSpotTrendResponseDTO`

字段：
- `unit`：单位，固定为 `元/MWh`
- `weekRule`：周规则，固定为 `周六到下周五`
- `maxWeekKey`：当前数据可返回的最大周数
- `selectedLastDataWeekKey`：本次请求使用的最后数据周数
- `regionId`：当前区域ID（`7` 归并后返回 `4`）
- `regionName`：当前区域名称
- `xAxis`：最近 10 周周标签
- `series[]`：该区域内各省份（公司）折线
- `companies[]`：该区域内各省份（公司）每周明细（`companyName`, `weeks[].avgSpotPrice`）

---

## 5) 周报图导出（ZIP）

`POST /export-charts`

### 请求体
- `lastDataWeekKey`：周标识（可选），为空时默认最大可用周
- `charts[]`：固定 9 张图
  - `imageBase64`：图像 base64（可传 `data:image/...;base64,...`）
  - 按以下顺序上传（后端按顺序命名）：
    1. `2026年省级现货实时均价走势`
    2. `公司日清分电能量电价走势`
    3. `中长期交易量价走势`
    4. `东北区域现货实时均价走势`
    5. `西北区域现货实时均价走势`
    6. `华北区域现货实时均价走势`
    7. `华中区域现货实时均价走势`
    8. `华东区域现货实时均价走势`
    9. `南方区域现货实时均价走势`

### 返回
- 二进制 ZIP 文件（`application/zip`）
- 压缩包名称：`周报用图-YYYYMMDD.zip`（`YYYYMMDD` 为所选周周五日期）
- 压缩包内固定 9 张 JPG 图，文件名为“图标题-YYYYMMDD.jpg”

---

## regionId 取值（区域）

- `1` 东北
- `2` 西北
- `3` 华北
- `4 + 7` 合并华中（返回名称仍为 `华中`）
- `5` 华东
- `6` 南方

---

## 说明（重要）

- 区域口径按 `companies.region_id`；`7` 并入 `4`，`8` 不纳入图4。
