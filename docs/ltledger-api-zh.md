# LT Ledger 接口文档（中文）

基线路径：`/api/longterm-ledger`

## 概述
- 请求统一使用 **ID 过滤**。
- `pivot` 返回公司级聚合表，不再返回 `raw`。
- 明细接口为 `company_detail`，用于弹窗/钻取。
- `options` 仅支持 `POST`，并按当前筛选动态返回可用 ID 选项。

## 请求模型

### 1) LTLedgerQuery（用于 pivot / company_detail / summary / trend）
- `companyId: long`（仅 `company_detail` 会用到）
- `genTypeIds: int[]`
- `transactionTypeIds: int[]`
- `transactionPeriodIds: int[]`
- `contractStartDate: string` (`YYYY-MM-DD`)
- `contractEndDate: string` (`YYYY-MM-DD`)
- `isGreen: boolean`

### 2) LTLedgerOptionsQuery（仅用于 options）
- `genTypeIds: int[]`
- `transactionTypeIds: int[]`
- `transactionPeriodIds: int[]`
- `contractStartDate: string` (`YYYY-MM-DD`)
- `contractEndDate: string` (`YYYY-MM-DD`)
- `isGreen: boolean`

> `options` 请求体 **不包含** `companyId`。

## 响应模型

### 1) LTLedgerResponse（pivot）
- `table: List<Map<String,Object>>`
  - 当前列：`companyId`, `companyName`, `chngTransactionAmount`, `chngTradedPrice`, `weightedBenchmarkPrice`
- `meta: { companyCount, rowCount, fullCompanyCoverage }`

### 2) LTLedgerDTO（company_detail）
明细字段包括：`id, transactionId, companyId, companyName, transactionTypeId, transactionTypeName, genTypeId, genTypeName, transactionPeriodId, transactionPeriodName, contractStartDate, contractEndDate, isGreen, basePrice, marketAvgPrice, chngTransactionAmount, chngTradedPrice ...`

### 3) LTLedgerFilterOptionsDTO（options）
- `transactionTypeIds: int[]`
- `genTypeIds: int[]`
- `transactionPeriodIds: int[]`
- `greenPowerOptions: int[]`（0/1）
- `minContractDate: string`
- `maxContractDate: string`

## 接口列表

### 1. POST `/pivot`
公司级透视聚合。

请求示例：
```json
{
  "genTypeIds": [1],
  "transactionTypeIds": [1],
  "transactionPeriodIds": [1],
  "contractStartDate": "2026-01-01",
  "contractEndDate": "2026-03-31",
  "isGreen": false
}
```

响应示例：
```json
{
  "table": [
    {
      "companyId": 1,
      "companyName": "河北",
      "chngTransactionAmount": 1101.57,
      "chngTradedPrice": 411.33,
      "weightedBenchmarkPrice": 363.83
    }
  ],
  "meta": {
    "companyCount": 32,
    "rowCount": 1280,
    "fullCompanyCoverage": false
  }
}
```

### 2. POST `/company_detail`
按筛选返回交易明细；传 `companyId` 只查某公司。用于点击pivot表中的某一行来显示其详细数据

请求示例：
```json
{
  "companyId": 1,
  "genTypeIds": [1],
  "transactionTypeIds": [1],
  "transactionPeriodIds": [1],
  "contractStartDate": "2026-01-01",
  "contractEndDate": "2026-03-31",
  "isGreen": false
}
```

响应：`List<LTLedgerDTO>`

### 3. POST `/summary`
汇总指标：
- `totalTradedPower`
- `weightedBenchmarkPrice`
- `chngTradedPrice`
- `companyCount`

### 4. POST `/trend`
按 `contract_start_date` 月份聚合趋势，返回：
- `period` (`YYYY-MM`)
- `tradedPower`
- `weightedBenchmarkPrice`
- `chngTradedPrice`

### 5. POST `/options`
动态联动选项（ID 口径）。

首次可传空对象：
```json
{}
```

响应示例：
```json
{
  "transactionTypeIds": [1, 2],
  "genTypeIds": [1, 2, 3],
  "transactionPeriodIds": [1, 2, 3, 4],
  "greenPowerOptions": [0, 1],
  "minContractDate": "2018-01-01",
  "maxContractDate": "2026-06-30"
}
```

## 已下线接口
- `POST /detail`（已改名为 `/company_detail`）
- `POST /data`（已删除）
- `GET /options`（已删除，仅保留 `POST /options`）
