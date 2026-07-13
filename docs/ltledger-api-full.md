Long-term Ledger (ltledger) Full API Documentation

Base path: /api/longterm-ledger

Overview
This document specifies the full backend API that implements the Streamlit longterm_ledger functionality.
- Frontend sends filter values by name (strings) — e.g., "发电类型":"煤电", "交易类型":"直接" — server maps names to IDs internally then queries DB by IDs (index-friendly).
- Endpoints that accept complex filters use POST with JSON body. Lightweight endpoints use GET.

Schemas (Java class equivalents)
- LTLedgerQuery (request)
  - companyIds: [long] (optional)
  - companyNames: [string] (optional, preferred)
  - genTypeIds: [int]
  - genTypeNames: [string]
  - transactionTypeIds: [int]
  - transactionTypes: [string]
  - transactionPeriodIds: [int]
  - transactionPeriodNames: [string]
  - contractPeriods: [string] (raw Excel strings)
  - isGreen: boolean
  - transactionDate: string (YYYY-MM-DD)
  - contractStartDate: string (YYYY-MM-DD)
  - contractEndDate: string (YYYY-MM-DD)

- LTLedgerDTO (response row)
  - id, transactionId
  - companyId, companyName, place, transactionDate, transactionName
  - transactionTypeId, transactionTypeName, genTypeId, genTypeName
  - transactionPeriodId, transactionPeriodName, transactionStartYear, transactionEndYear
  - contractStartDate, contractEndDate, isGreen, isCheap
  - basePrice, marketSize, marketParticipationCapacity, marketAvgPrice
  - chngParticipationCapacity, chngTransactionAmount, chngAvgPrice, envPremium
  - dataSource, note, createdAt

- LTLedgerResponse (pivot response)
  - table: List<Map<string,object>> (company rows with dynamic pivot columns)
  - raw: List<LTLedgerDTO> (optional raw rows used to build pivot)
  - filters: { transactionTypes[], powerTypes[], transactionPeriods[], contractStartDates[], contractEndDates[], greenPowerOptions[] }
  - meta: { companyCount, rowCount, fullCompanyCoverage }

Endpoints
1) POST /pivot
- Request: LTLedgerQuery (JSON) — frontend should prefer names; server maps to ids
- Response: LTLedgerResponse
- Example request:
{
  "genTypeNames": ["煤电"],
  "transactionTypes": ["直接"],
  "contractStartDate": "2025-01-01",
  "contractEndDate": "2025-12-31",
  "isGreen": true
}

2) POST /detail
- Request: LTLedgerQuery
- Response: List<LTLedgerDTO>
- Purpose: raw rows for export/debug

3) POST /data
- Compatibility: returns aggregated company-level list (existing behavior)

4) POST /summary
- Request: LTLedgerQuery
- Response: LTLedgerSummaryDTO { totalTradedPower, weightedBenchmarkPrice, huanengTradedPrice, companyCount }

5) POST /trend
- Request: LTLedgerQuery
- Response: List<LTLedgerTrendDTO> [{period: "YYYY-MM", tradedPower, weightedBenchmarkPrice, huanengTradedPrice}]

6) GET /options
- Response: LTLedgerFilterOptionsDTO (transactionTypes, powerTypes, transactionPeriods, greenPowerOptions, minContractDate, maxContractDate)

Implementation notes
- Server maps names to IDs by querying lookup tables (companies, gen_types, transaction_types, transaction_periods) using helper mapper queries.
- All DB filtering is by IDs for performance; server attaches names to DTOs before response.
- Pivot generation uses PivotServices.buildPivot that creates dynamic columns per selected dimension combination. This implements a Multi-dimensional Pivot (multi-dimension pivot comparison).
- Caching: for heavy repeated pivot queries consider server-side caching keyed by request-body hash.

Next Steps
- Implement frontend REST calls to POST /pivot and render returned table directly.
- Add OpenAPI/Swagger file and example responses (I can generate automatically).

