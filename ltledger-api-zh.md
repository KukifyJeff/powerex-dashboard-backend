# LT Ledger 接口文档（中文）

基线路径：/api/longterm-ledger

概述
- 设计原则：请求仅使用 id 作为过滤条件（保持与 SpotTracking 一致）。后端返回 name 字段供前端直接展示。
- 后端不接受公司过滤（始终返回所有公司作为 pivot 的行基准），companyName 仅出现在响应中。

请求通用类型（LTLedgerQuery）
- genTypeIds: [int] 可选
- transactionTypeIds: [int] 可选
- transactionPeriodIds: [int] 可选
- contractStartDate: string (YYYY-MM-DD) 可选
- contractEndDate: string (YYYY-MM-DD) 可选
- isGreen: boolean 可选
- transactionDate: string (YYYY-MM-DD) 可选

响应主要类型
- LTLedgerDTO（明细行，响应包含）
  - id, transactionId
  - companyId, companyName
  - place, transactionDate, transactionName
  - transactionTypeId, transactionTypeName
  - genTypeId, genTypeName
  - transactionPeriodId, transactionPeriodName
  - contractStartDate, contractEndDate, isGreen, isCheap
  - basePrice, marketSize, marketParticipationCapacity, marketAvgPrice
  - chngParticipationCapacity, chngTransactionAmount, chngTradedPrice, envPremium
  - dataSource, note, createdAt

- LTLedgerResponse（pivot 返回）
  - table: List<Map<string,object>>（每行包含 companyName 与统计列）
  - raw: List<LTLedgerDTO>
  - filters: LTLedgerFilterOptionsDTO
  - meta: { companyCount, rowCount, fullCompanyCoverage }

- LTLedgerFilterOptionsDTO（/options 返回）
  - transactionTypes: [string]
  - genTypes: [string]
  - transactionPeriods: [string]
  - greenPowerOptions: [string]
  - minContractDate: string
  - maxContractDate: string

Endpoints（示例请求与 curl）

1) POST /api/longterm-ledger/pivot
- 说明：返回按公司为行的 pivot 表（动态列），并返回 raw 明细（可选）。
- 请求示例（使用 id）：
```
{
  "genTypeIds": [2,3],
  "transactionTypeIds": [1],
  "contractStartDate": "2023-01-01",
  "contractEndDate": "2023-12-31",
  "isGreen": true
}
```
- curl：
```
curl -X POST http://localhost:8080/api/longterm-ledger/pivot \
  -H "Content-Type: application/json" \
  -d '{"genTypeIds":[2,3],"transactionTypeIds":[1],"contractStartDate":"2023-01-01","contractEndDate":"2023-12-31","isGreen":true}'
```

- 响应示例（简化）：
```
{
  "table":[
    {"companyName":"华能集团","totalAmount":1.2345,"weightedPrice":120.50},
    {"companyName":"国电","totalAmount":0.5678,"weightedPrice":110.30}
  ],
  "raw":[ /* LTLedgerDTO 列表 */ ],
  "meta":{"companyCount":2,"rowCount":10,"fullCompanyCoverage":false}
}
```

2) POST /api/longterm-ledger/detail
- 说明：返回明细行（用于导出、审计）。
- 请求同上（使用 id）。
- curl：
```
curl -X POST http://localhost:8080/api/longterm-ledger/detail -H "Content-Type: application/json" -d '<payload>'
```
- 响应：List<LTLedgerDTO>

3) POST /api/longterm-ledger/data
- 说明：兼容老用法，返回公司级别或聚合形式。
- 请求/响应与 /pivot /detail 类似（兼容场景）。

4) POST /api/longterm-ledger/summary
- 说明：返回汇总指标（全局）。
- 请求示例：
```
{"transactionTypeIds":[1]}
```
- 响应示例：
```
{
  "totalTradedPower": 12.3456,
  "weightedBenchmarkPrice": 115.50,
  "chngTradedPrice": 118.20,
  "companyCount": 20
}
```

5) POST /api/longterm-ledger/trend
- 说明：按年月返回时间序列（period 格式 YYYY-MM）。
- 请求示例：
```
{"genTypeIds":[2],"contractStartDate":"2023-01-01","contractEndDate":"2023-12-31"}
```
- 响应示例：
```
[
  {"period":"2023-01","tradedPower":1.23,"weightedBenchmarkPrice":110.5,"chngTradedPrice":112.0},
  {"period":"2023-02",...}
]
```

6) GET /api/longterm-ledger/options
- 说明：返回下拉选择项与日期范围。
- curl：
```
curl http://localhost:8080/api/longterm-ledger/options
```
- 响应示例：
```
{
  "transactionTypes":["直接","外送"],
  "genTypes":["煤电","光伏","风电"],
  "transactionPeriods":["年度","月度"],
  "greenPowerOptions":["Y","N"],
  "minContractDate":"2018-01-01",
  "maxContractDate":"2026-06-30"
}
```

错误 & 边界行为
- 请求仅使用 ids；若传入名字（names），本接口将不再保证支持。
- 过滤条件导致无数据返回空表，HTTP 200。
- 非法参数返回 400 并带错误信息。

版本迁移建议
- 强制请求仅包含 *_Ids 字段，过渡期内可在前端通过 /options 获取 lookup 并维护 id ↔ name 映射。

