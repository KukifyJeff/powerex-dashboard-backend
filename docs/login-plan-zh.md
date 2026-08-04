# 用户登录功能改造计划（前端 / 后端 / 数据库）

## 1. 目标与范围

- 支持**账号密码登录**，登录成功后访问业务接口需带认证凭据。
- 采用 **JWT（Access Token）+ 刷新机制（Refresh Token）** 的标准方案。
- 支持最小角色控制（先做 `ADMIN` / `USER` 两类），后续可扩展到细粒度权限。

> 说明：当前仓库是后端仓库，本文中的“前端改造”用于指导前端仓库联动实施。

---

## 2. 数据库需要改什么

## 2.1 新增表

### `users`（用户主表）
- `id` BIGINT PK
- `username` VARCHAR(64) UNIQUE NOT NULL
- `password_hash` VARCHAR(255) NOT NULL（BCrypt）
- `display_name` VARCHAR(100) NULL
- `status` TINYINT NOT NULL DEFAULT 1（1=启用，0=禁用）
- `last_login_at` DATETIME NULL
- `created_at` DATETIME NOT NULL
- `updated_at` DATETIME NOT NULL

### `roles`（角色表）
- `id` BIGINT PK
- `code` VARCHAR(32) UNIQUE NOT NULL（如 `ADMIN`、`USER`）
- `name` VARCHAR(64) NOT NULL

### `user_roles`（用户角色关系表）
- `user_id` BIGINT NOT NULL
- `role_id` BIGINT NOT NULL
- 复合唯一键：`(user_id, role_id)`

### `refresh_tokens`（刷新令牌表，推荐）
- `id` BIGINT PK
- `user_id` BIGINT NOT NULL
- `token_hash` VARCHAR(255) NOT NULL（只存哈希）
- `expires_at` DATETIME NOT NULL
- `revoked` TINYINT NOT NULL DEFAULT 0
- `created_at` DATETIME NOT NULL
- 索引：`user_id`、`expires_at`

## 2.2 初始化与迁移

- 新增 SQL 迁移脚本（建议放在 `src/main/resources/sql/`）。
- 初始化一个管理员账号（密码为临时密码，首次登录后强制改密可作为二期）。
- 为 `username`、`code`、`user_roles(user_id, role_id)` 建唯一索引。

---

## 3. 后端需要改什么（本仓库）

## 3.1 依赖与配置

- `pom.xml` 增加：
  - `spring-boot-starter-security`
  - `jjwt-api` / `jjwt-impl` / `jjwt-jackson`（或你偏好的 JWT 库）
- `application.properties` 增加：
  - `auth.jwt.secret`
  - `auth.jwt.access-token-minutes`
  - `auth.jwt.refresh-token-days`

## 3.2 代码结构（建议新增模块）

- `controller/AuthController.java`
  - `POST /api/auth/login`
  - `POST /api/auth/refresh`
  - `POST /api/auth/logout`
  - `GET /api/auth/me`
- `services/auth/*`
  - 登录校验、密码比对、令牌签发与刷新、登出失效处理
- `mapper/auth/*` + `resources/mapper/auth/*.xml`
  - `users` / `roles` / `user_roles` / `refresh_tokens` 查询与写入
- `config/security/*`
  - Security 配置（白名单、鉴权规则）
  - JWT 过滤器（从 `Authorization: Bearer <token>` 解析用户）
- `request/auth/*`、`responses/auth/*`
  - `LoginRequest`、`RefreshRequest`、`AuthTokenResponse`、`CurrentUserResponse`

## 3.3 接口与鉴权规则

- 放行接口：
  - `/api/auth/login`
  - `/api/auth/refresh`
  - `/actuator/health`（如线上探活使用）
- 其余 `/api/**` 默认要求登录。
- `logout` 行为：
  - 若使用刷新令牌表：将当前 refresh token 标记 `revoked=1`。

## 3.4 安全细节（必须做）

- 密码仅存 `BCrypt` 哈希，不落明文。
- JWT 过期时间分离：Access 短时（例如 30 分钟），Refresh 长时（例如 7 天）。
- 令牌轮换：refresh 成功后，旧 refresh token 立即作废。
- 登录失败返回统一错误，不暴露“用户名不存在/密码错误”的细节差异。

---

## 4. 前端需要改什么（联动项）

## 4.1 页面与状态

- 新增登录页（用户名、密码、错误提示、加载态）。
- 增加全局登录态存储（用户信息、access token、refresh token）。
- 应用启动时尝试恢复登录态（检查 token 是否可用）。

## 4.2 请求链路

- HTTP 拦截器自动附带 `Authorization` 头。
- `401` 自动触发 `refresh`，成功后重放原请求；失败则清理登录态并跳转登录页。
- 主动登出调用 `/api/auth/logout` 并清空本地凭据。

## 4.3 路由与权限

- 受保护路由增加登录守卫。
- 根据 `/api/auth/me` 返回的角色做菜单/按钮级显示控制（先做页面级即可）。

---

## 5. 联调接口草案

### `POST /api/auth/login`
- 请求：`{ "username": "...", "password": "..." }`
- 响应：`{ "accessToken": "...", "refreshToken": "...", "expiresIn": 1800, "user": { ... } }`

### `POST /api/auth/refresh`
- 请求：`{ "refreshToken": "..." }`
- 响应：同登录响应（新 token）

### `POST /api/auth/logout`
- 请求：`{ "refreshToken": "..." }`（或从上下文获取）
- 响应：`{ "success": true }`

### `GET /api/auth/me`
- 响应：`{ "id": 1, "username": "...", "displayName": "...", "roles": ["ADMIN"] }`

---

## 6. 建议实施顺序

1. **数据库**：建表 + 索引 + 初始化管理员。
2. **后端**：登录/刷新/登出/me 四个接口 + 全局鉴权拦截。
3. **前端**：登录页 + token 存储 + 请求拦截器 + 路由守卫。
4. **联调**：覆盖登录成功、过期刷新、刷新失败、登出、禁用用户等核心场景。

---

## 7. 本期最小可交付（MVP）

- 账号密码登录
- Access/Refresh token
- `/api/**` 接口鉴权
- 前端登录态维护与 401 自动刷新

后续二期可加：验证码、登录风控、审计日志、改密/忘记密码、多端会话管理。
