# 文档管理 API

## 1. 文档上传

- 方法: POST
- 路径: /api/documents/upload
- Content-Type: multipart/form-data
- 认证: 需要 JWT Bearer Token

### 请求参数
- file: Markdown 文件（必填），支持 .md / .markdown / .mdx
- directory: 分目录，例 `team/ops`，可选
- title: 文档标题，可选
- description: 文档说明，可选

### 示例
```bash
curl -X POST http://localhost:8080/api/documents/upload \
  -H "Authorization: Bearer <token>" \
  -F "file=@README.md;type=text/markdown" \
  -F "directory=team/ops" \
  -F "title=README" \
  -F "description=项目说明"
```

### 成功响应
```json
{
  "success": true,
  "message": "Document uploaded successfully.",
  "data": {
    "id": 1,
    "title": "README",
    "description": "项目说明",
    "originalName": "README.md",
    "storedName": "README.md",
    "directory": "team/ops",
    "filePath": "/.../uploads/docs/team/ops/README.md",
    "contentType": "text/markdown",
    "sizeBytes": 1234,
    "uploadedBy": "admin",
    "createdAt": "2026-08-27T08:00:00",
    "updatedAt": "2026-08-27T08:00:00"
  }
}
```

## 2. 文档列表

- 方法: GET
- 路径: /api/documents
- 认证: 需要 JWT Bearer Token

### 查询参数
- directory: 可选，按目录过滤，如 `?directory=team/ops`

### 示例
```bash
curl -H "Authorization: Bearer <token>" \
  "http://localhost:8080/api/documents?directory=team/ops"
```

### 响应示例
```json
[
  {
    "id": 1,
    "title": "README",
    "description": "项目说明",
    "originalName": "README.md",
    "storedName": "README.md",
    "directory": "team/ops",
    "filePath": "/.../uploads/docs/team/ops/README.md",
    "contentType": "text/markdown",
    "sizeBytes": 1234,
    "uploadedBy": "admin",
    "createdAt": "2026-08-27T08:00:00",
    "updatedAt": "2026-08-27T08:00:00"
  }
]
```

## 3. 目录列表

- 方法: GET
- 路径: /api/documents/directories
- 认证: 需要 JWT Bearer Token

### 示例
```bash
curl -H "Authorization: Bearer <token>" \
  http://localhost:8080/api/documents/directories
```

### 响应示例
```json
["team", "team/ops", "knowledge/base"]
```

## 4. 文档详情与内容查看

- 方法: GET
- 路径: /api/documents/{id}
- 认证: 需要 JWT Bearer Token

### 示例
```bash
curl -H "Authorization: Bearer <token>" \
  http://localhost:8080/api/documents/1
```

### 成功响应
```json
{
  "document": {
    "id": 1,
    "title": "README",
    "description": "项目说明",
    "originalName": "README.md",
    "storedName": "README.md",
    "directory": "team/ops",
    "filePath": "/.../uploads/docs/team/ops/README.md",
    "contentType": "text/markdown",
    "sizeBytes": 1234,
    "uploadedBy": "admin",
    "createdAt": "2026-08-27T08:00:00",
    "updatedAt": "2026-08-27T08:00:00"
  },
  "content": "# README\n\n项目说明\n"
}
```

## 5. 文档目录树

- 方法: GET
- 路径: /api/documents/tree
- 认证: 需要 JWT Bearer Token

### 示例
```bash
curl -H "Authorization: Bearer <token>"   http://localhost:8080/api/documents/tree
```

### 响应示例
```json
[
  {
    "key": "dir:team",
    "label": "team",
    "type": "directory",
    "path": "team",
    "documentId": null,
    "children": [
      {
        "key": "dir:team/ops",
        "label": "ops",
        "type": "directory",
        "path": "team/ops",
        "documentId": null,
        "children": [
          {
            "key": "file:1",
            "label": "README",
            "type": "file",
            "path": "team/ops/README.md",
            "documentId": 1,
            "children": []
          }
        ]
      }
    ]
  }
]
```

## 6. 文档预览

- 方法: GET
- 路径: /api/documents/{id}/preview
- 认证: 需要 JWT Bearer Token

### 示例
```bash
curl -H "Authorization: Bearer <token>"   http://localhost:8080/api/documents/1/preview
```

### 响应示例
```json
{
  "document": {
    "id": 1,
    "title": "README",
    "directory": "team/ops",
    "storedName": "README.md"
  },
  "content": "# README

项目说明
"
}
```

## 7. 文档重命名

- 方法: PATCH
- 路径: /api/documents/{id}/rename
- 认证: 需要 JWT Bearer Token

### 请求体
```json
{
  "title": "新标题",
  "directory": "team/ops",
  "newFileName": "new-name.md"
}
```

### 示例
```bash
curl -X PATCH http://localhost:8080/api/documents/1/rename   -H "Authorization: Bearer <token>"   -H "Content-Type: application/json"   -d '{
    "title": "新标题",
    "directory": "team/ops",
    "newFileName": "new-name.md"
  }'
```

## 8. 文档删除

- 方法: DELETE
- 路径: /api/documents/{id}
- 认证: 需要 JWT Bearer Token

### 示例
```bash
curl -X DELETE http://localhost:8080/api/documents/1   -H "Authorization: Bearer <token>"
```

### 成功响应
```text
Document deleted successfully.
```

## 9. 认证说明

所有接口都要求请求头携带：

```http
Authorization: Bearer <JWT_TOKEN>
```

登录接口：
```bash
POST /auth/login
```

## 10. 说明

- 默认存储目录：./uploads/docs
- 目录路径会进行规范化，禁止 `..` 等非法路径
- 上传的文件支持 Markdown 文件格式(.md / .markdown / .mdx)
- 删除和重命名会同步更新文件系统及数据库记录
