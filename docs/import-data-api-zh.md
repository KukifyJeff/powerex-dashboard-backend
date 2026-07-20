# ImportData 接口文档（中文）

基线路径：`/api/import-data`

---

## 1) 上传中长期文件并规范化（不直接发布）

### 接口地址
`POST /api/import-data/upload/longterm`

### 接口用处
批量上传多个**中长期** Excel 文件，执行规范化后写入导入任务与 staging 表，返回任务详情与文件处理结果。

### 请求体格式
- `Content-Type: multipart/form-data`
- 表单字段：
  - `files`（必填，可重复）：上传文件（`.xlsx/.xls`）
- Query 参数：
  - `createdBy`（可选，string）：发起人标识

### 示例请求体
```bash
curl -X POST 'http://localhost:8080/api/import-data/upload?createdBy=yuqi' \
  -F 'files=@/path/2026_longterm_01.xlsx' \
  -F 'files=@/path/2026_longterm_02.xlsx'
```

### 返回体格式
```json
{
  "success": "boolean",
  "message": "string",
  "data": {
    "jobId": "long",
    "status": "string",
    "uploadedFileCount": "int",
    "longtermRowCount": "int",
    "spotRowCount": "int",
    "failedFileCount": "int",
    "errorMessage": "string|null",
    "createdAt": "yyyy-MM-dd HH:mm:ss",
    "normalizedAt": "yyyy-MM-dd HH:mm:ss|null",
    "confirmedAt": "yyyy-MM-dd HH:mm:ss|null",
    "files": [
      {
        "fileName": "string",
        "dataType": "LONGTERM|UNKNOWN",
        "status": "SUCCESS|PARTIAL|FAILED",
        "totalRows": "int",
        "normalizedRows": "int",
        "skippedRows": "int",
        "errorCount": "int",
        "errorMessage": "string|null"
      }
    ]
  }
}
```

### 示例返回体
```json
{
  "success": true,
  "message": "Normalization completed",
  "data": {
    "jobId": 12,
    "status": "NORMALIZED",
    "uploadedFileCount": 2,
    "longtermRowCount": 1532,
    "spotRowCount": 0,
    "failedFileCount": 0,
    "errorMessage": null,
    "createdAt": "2026-07-14 16:20:18",
    "normalizedAt": "2026-07-14 16:20:25",
    "confirmedAt": null,
    "files": [
      {
        "fileName": "2026_longterm.xlsx",
        "dataType": "LONGTERM",
        "status": "SUCCESS",
        "totalRows": 1532,
        "normalizedRows": 1532,
        "skippedRows": 0,
        "errorCount": 0,
        "errorMessage": null
      },
      {
        "fileName": "2026_longterm_02.xlsx",
        "dataType": "LONGTERM",
        "status": "SUCCESS",
        "totalRows": 1268,
        "normalizedRows": 1268,
        "skippedRows": 0,
        "errorCount": 0,
        "errorMessage": null
      }
    ]
  }
}
```

---

## 2) 上传现货文件并规范化（不直接发布）

### 接口地址
`POST /api/import-data/upload/spot`

### 接口用处
批量上传多个**现货** Excel 文件，执行规范化后写入导入任务与 staging 表，返回任务详情与文件处理结果。

### 请求体格式
- `Content-Type: multipart/form-data`
- 表单字段：
  - `files`（必填，可重复）：上传文件（`.xlsx/.xls`）
- Query 参数：
  - `createdBy`（可选，string）：发起人标识

### 示例请求体
```bash
curl -X POST 'http://localhost:8080/api/import-data/upload/spot?createdBy=yuqi' \
  -F 'files=@/path/2026_spot_coal.xlsx' \
  -F 'files=@/path/2026_spot_wind.xlsx'
```

### 返回体格式
与 `/upload/longterm` 相同，`dataType` 为 `SPOT|UNKNOWN`，`longtermRowCount` 一般为 `0`。

### 示例返回体
```json
{
  "success": true,
  "message": "Normalization completed",
  "data": {
    "jobId": 13,
    "status": "NORMALIZED",
    "uploadedFileCount": 2,
    "longtermRowCount": 0,
    "spotRowCount": 824,
    "failedFileCount": 0,
    "errorMessage": null,
    "createdAt": "2026-07-14 16:25:18",
    "normalizedAt": "2026-07-14 16:25:22",
    "confirmedAt": null,
    "files": [
      {
        "fileName": "2026_spot_coal.xlsx",
        "dataType": "SPOT",
        "status": "SUCCESS",
        "totalRows": 430,
        "normalizedRows": 430,
        "skippedRows": 0,
        "errorCount": 0,
        "errorMessage": null
      }
    ]
  }
}
```

---

## 3) 查询导入任务详情

### 接口地址
`GET /api/import-data/jobs/{jobId}`

### 接口用处
按任务 ID 查询导入任务当前状态和文件处理明细。

### 请求体格式
无（Path 参数：`jobId`）。

### 示例请求体
`GET /api/import-data/jobs/12`

### 返回体格式
`ImportDataJobResponse`
```json
{
  "jobId": "long",
  "status": "string",
  "uploadedFileCount": "int",
  "longtermRowCount": "int",
  "spotRowCount": "int",
  "failedFileCount": "int",
  "errorMessage": "string|null",
  "createdAt": "yyyy-MM-dd HH:mm:ss",
  "normalizedAt": "yyyy-MM-dd HH:mm:ss|null",
  "confirmedAt": "yyyy-MM-dd HH:mm:ss|null",
  "files": [
    {
      "fileName": "string",
      "dataType": "string",
      "status": "string",
      "totalRows": "int",
      "normalizedRows": "int",
      "skippedRows": "int",
      "errorCount": "int",
      "errorMessage": "string|null"
    }
  ]
}
```

### 示例返回体
```json
{
  "jobId": 12,
  "status": "NORMALIZED",
  "uploadedFileCount": 2,
  "longtermRowCount": 1532,
  "spotRowCount": 824,
  "failedFileCount": 0,
  "errorMessage": null,
  "createdAt": "2026-07-14 16:20:18",
  "normalizedAt": "2026-07-14 16:20:25",
  "confirmedAt": null,
  "files": [
    {
      "fileName": "2026_longterm.xlsx",
      "dataType": "LONGTERM",
      "status": "SUCCESS",
      "totalRows": 1532,
      "normalizedRows": 1532,
      "skippedRows": 0,
      "errorCount": 0,
      "errorMessage": null
    }
  ]
}
```

---

## 4) 管理员确认发布新版本

### 接口地址
`POST /api/import-data/confirm`

### 接口用处
管理员密码校验通过后，将指定任务发布为新版本，并切换为 active 版本（同时记录恢复点）。

### 请求体格式
```json
{
  "jobId": "long, required",
  "adminPassword": "string, required",
  "remark": "string, optional"
}
```

### 示例请求体
```json
{
  "jobId": 12,
  "adminPassword": "YourStrongPassword!",
  "remark": "2026-07-14 全量导入发布"
}
```

### 返回体格式
```json
{
  "success": "boolean",
  "message": "string",
  "jobId": "long|null",
  "versionId": "long|null",
  "versionCode": "string|null"
}
```

### 示例返回体
```json
{
  "success": true,
  "message": "Import confirmed and activated",
  "jobId": 12,
  "versionId": 5,
  "versionCode": "IMP-20260714-162102"
}
```

---

## 5) 查询版本列表

### 接口地址
`GET /api/import-data/versions`

### 接口用处
查询最近版本历史（含 active/inactive、行数、激活与回滚时间等）。

### 请求体格式
无。

### 示例请求体
`GET /api/import-data/versions`

### 返回体格式
`List<ImportDataVersionItem>`
```json
[
  {
    "id": "long",
    "versionCode": "string",
    "sourceJobId": "long",
    "status": "ACTIVE|INACTIVE",
    "longtermRowCount": "int",
    "spotRowCount": "int",
    "createdAt": "yyyy-MM-dd HH:mm:ss",
    "activatedAt": "yyyy-MM-dd HH:mm:ss|null",
    "rolledBackAt": "yyyy-MM-dd HH:mm:ss|null",
    "remark": "string|null"
  }
]
```

### 示例返回体
```json
[
  {
    "id": 5,
    "versionCode": "IMP-20260714-162102",
    "sourceJobId": 12,
    "status": "ACTIVE",
    "longtermRowCount": 1532,
    "spotRowCount": 824,
    "createdAt": "2026-07-14 16:21:02",
    "activatedAt": "2026-07-14 16:21:08",
    "rolledBackAt": null,
    "remark": "2026-07-14 全量导入发布"
  }
]
```

---

## 6) 查询恢复点（binlog 位点）

### 接口地址
`GET /api/import-data/restore-points`

### 接口用处
查询发布/回滚前后的 MySQL 恢复点，便于 `mysqlbinlog` / PITR 运维恢复。

### 请求体格式
无。

### 示例请求体
`GET /api/import-data/restore-points`

### 返回体格式
`List<ImportDataRestorePointItem>`
```json
[
  {
    "id": "long",
    "eventType": "BEFORE_CONFIRM|AFTER_CONFIRM|BEFORE_ROLLBACK|AFTER_ROLLBACK",
    "triggerAction": "CONFIRM|ROLLBACK",
    "referenceJobId": "long|null",
    "referenceVersionId": "long|null",
    "fromVersionId": "long|null",
    "toVersionId": "long|null",
    "binlogFile": "string",
    "binlogPosition": "long",
    "gtidSet": "string|null",
    "operator": "string|null",
    "note": "string|null",
    "createdAt": "yyyy-MM-dd HH:mm:ss"
  }
]
```

### 示例返回体
```json
[
  {
    "id": 21,
    "eventType": "BEFORE_CONFIRM",
    "triggerAction": "CONFIRM",
    "referenceJobId": 12,
    "referenceVersionId": 5,
    "fromVersionId": 4,
    "toVersionId": 5,
    "binlogFile": "binlog.000154",
    "binlogPosition": 875321,
    "gtidSet": null,
    "operator": "yuqi",
    "note": "before confirm import version",
    "createdAt": "2026-07-14 16:21:01"
  }
]
```

---

## 7) 按版本 ID 回滚

### 接口地址
`POST /api/import-data/rollback`

### 接口用处
按指定 `versionId` 回滚到历史版本，并记录回滚前后恢复点。

### 请求体格式
```json
{
  "versionId": "long, required",
  "adminPassword": "string, required",
  "reason": "string, optional"
}
```

### 示例请求体
```json
{
  "versionId": 4,
  "adminPassword": "YourStrongPassword!",
  "reason": "发布后发现异常，回滚到上一个稳定版本"
}
```

### 返回体格式
```json
{
  "success": "boolean",
  "message": "string",
  "jobId": "long|null",
  "versionId": "long|null",
  "versionCode": "string|null"
}
```

### 示例返回体
```json
{
  "success": true,
  "message": "Rollback completed",
  "jobId": null,
  "versionId": 4,
  "versionCode": "IMP-20260713-181030"
}
```

---

## 8) 按最近 N 次回滚

### 接口地址
`POST /api/import-data/rollback/recent`

### 接口用处
从当前 active 版本向前回滚 `steps` 次（例如 1=上一个版本，2=上上个版本）。

### 请求体格式
```json
{
  "steps": "int, required, 1~20",
  "adminPassword": "string, required",
  "reason": "string, optional"
}
```

### 示例请求体
```json
{
  "steps": 2,
  "adminPassword": "YourStrongPassword!",
  "reason": "回退两次到本周一稳定版本"
}
```

### 返回体格式
```json
{
  "success": "boolean",
  "message": "string",
  "jobId": "long|null",
  "versionId": "long|null",
  "versionCode": "string|null"
}
```

### 示例返回体
```json
{
  "success": true,
  "message": "Rollback completed",
  "jobId": null,
  "versionId": 3,
  "versionCode": "IMP-20260712-093500"
}
```

---

## 常见错误响应

以下接口使用 `ResponseStatusException` 返回错误，常见 HTTP 状态：
- `400 Bad Request`：参数错误、版本不存在、回滚步数非法
- `403 Forbidden`：管理员密码错误
- `404 Not Found`：任务不存在
- `409 Conflict`：任务状态不允许发布，或流程冲突

示例错误返回（Spring Boot 默认错误结构）：
```json
{
  "timestamp": "2026-07-14T16:23:18.120+08:00",
  "status": 403,
  "error": "Forbidden",
  "message": "Invalid admin password",
  "path": "/api/import-data/confirm"
}
```

---

## 兼容接口（不建议新接入）

- `POST /api/import-data/upload`
  - 混合上传入口（可同时识别中长期/现货）
  - 为兼容历史调用保留；新前端建议使用分离接口：
    - `/api/import-data/upload/longterm`
    - `/api/import-data/upload/spot`
