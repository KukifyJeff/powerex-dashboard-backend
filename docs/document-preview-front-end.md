# 文档预览页与目录树前端组件接口需求

## 1. 页面结构

前端页面建议分成两块：
- 左侧：目录树（按目录分组，支持展开/收起）
- 右侧：Markdown 内容区（预览/渲染）

## 2. 接口需求

### 2.1 获取目录树

- 方法: GET
- 路径: /api/documents/tree
- 鉴权: Bearer Token

返回结构示例：
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

前端组件建议：
- 采用树组件渲染 children
- `type === "directory"` 时可展开
- `type === "file"` 时点击打开 Markdown 详情
- `documentId` 作为选中文档的唯一标识

### 2.2 获取文档详情（Markdown 原始内容）

- 方法: GET
- 路径: /api/documents/{id}
- 或 /api/documents/{id}/preview
- 鉴权: Bearer Token

返回：
```json
{
  "document": {
    "id": 1,
    "title": "README",
    "directory": "team/ops",
    "originalName": "README.md",
    "storedName": "README.md",
    "uploadedBy": "admin",
    "createdAt": "2026-08-27T08:00:00"
  },
  "content": "# README\n\nHello world\n"
}
```

前端建议：
- `content` 作为 markdown source
- 将其交给 `react-markdown` / `marked` / `markdown-it` 渲染
- `document.title` 显示页面标题

## 3. 推荐前端状态

```ts
interface DocumentTreeNode {
  key: string;
  label: string;
  type: 'directory' | 'file';
  path: string;
  documentId?: number | null;
  children: DocumentTreeNode[];
}

interface DocumentDetailResponse {
  document: {
    id: number;
    title: string;
    directory: string;
    originalName: string;
    storedName: string;
    uploadedBy: string;
    createdAt: string;
    updatedAt?: string;
  };
  content: string;
}
```

## 4. 页面交互建议

- 初次进入页面：调用 `/api/documents/tree` 获取树
- 点击目录：展开/折叠
- 点击文件：调用 `/api/documents/{id}` 拉取内容并渲染
- 目录筛选：可支持 `?directory=team/ops` 作为列表过滤

## 5. 预览渲染建议

前端建议使用：
- react-markdown
- remark-gfm
- rehype-raw（如需支持 html）

示例：
```tsx
import ReactMarkdown from 'react-markdown';

<ReactMarkdown>{content}</ReactMarkdown>
```

## 6. 备注

- 上传的文档默认支持 Markdown 格式
- 目录树是基于 `directory` 字段生成，不依赖物理文件系统结构
- 如果后续加“文档编辑/删除/重命名”，同样应复用这里的目录树接口
