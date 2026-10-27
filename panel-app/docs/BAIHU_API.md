# 白虎面板 API 参考（源码取证版）

> 来源：`baihu-panel` 仓库源码（本地 `temp/baihu-panel`，main 分支）
> 路由定义：`internal/router/api_routes.go`；接口实现：`internal/controllers/*.go`
> 本地仓库快照：8cfe9fa 2026-09-07。**以源码为准，接口有变动时重新取证。**

## 认证

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/v1/auth/login` | 登录，body `{username, password}` |
| POST | `/api/v1/auth/login/otp` | 两步验证登录（开启 OTP 后第一步只返回临时凭证 `otp_pending_token`，不下发 Cookie；第二步用 `{otp_pending_token, code}` 换正式 Cookie） |
| POST | `/api/v1/auth/logout` | 登出 |
| GET | `/api/v1/auth/me` | 当前用户 |
| GET/POST | `/api/v1/auth/otp/status` `/generate` `/enable` `/disable` | OTP 管理 |

## 文件与脚本（关键取证）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/v1/files/tree?path=` | **文件树。懒加载设计**：path 留空返回根列表（目录项带 `hasChildren` 标记、不下发 children）；传 path 返回该目录单层子项。`FileNode`：`name/path/isDir/modTime/hasChildren/children`（完整规格见 `BAIHU_SWAGGER.json`） |
| GET | `/api/v1/files/content?path=` | 读文件内容，`path` 必填（缺失返回 `path参数必填`）；文件不存在返回 500 `open ...: no such file or directory` |
| GET | `/api/v1/files/download` / `download-zip` | 单文件/打包下载 |
| POST | `/api/v1/files/content` | 保存内容 `{path, content}` |
| POST | `/api/v1/files/create` | 创建文件/目录 `{path, isDir}` |
| POST | `/api/v1/files/delete` `/rename` `/move` `/copy` | 删除/重命名/移动/复制 |
| POST | `/api/v1/files/upload` / `uploadfiles` | 上传 |
| GET/POST/PUT/DELETE | `/api/v1/scripts` | 脚本 CRUD |

### 文件树双协议说明（重要）
白虎存在**两套 API 层**，文件树契约同构（都是 `?path=` 懒加载 + `hasChildren`）：
1. **`/api/v1/*`（web 内部 API，JWT 鉴权）**——本 App 使用。注意：本地 `main` 分支源码的 `GetFileTree` 是全量 WalkDir 无参数（旧实现），**线上部署版本已按 swagger 契约实现了懒加载**（demo 站实测响应带 hasChildren）。App 按 `?path=` 懒加载对接，并带根列表误注入防御。
2. **`/open2api/v1/*`（对外 OpenAPI 层，`Authorization: Bearer <OpenAPI Token>`，设置页可生成 token）**——完整规格见 `BAIHU_SWAGGER.json`（Swagger 2.0，basePath `/open2api/v1`）。额外提供 `/files/trees?paths=a,b`（批量）、`/files/search?keyword=`、应用市场 `/apps`（部署/更新/回滚/自动更新）等。

## 其他常用

| 分组 | 接口 |
|---|---|
| 仪表盘 | GET `/api/v1/stats` `/taskstats` `/sendstats` `/sentence` |
| 任务 | `/api/v1/tasks` CRUD + `/bulk_save` `/batch-*` `/stop/:logID` `/tags` |
| 执行 | POST `/api/v1/execution/task/:id` `/command`；GET `/results` |
| 变量 | `/api/v1/env` CRUD + `/bulk_save` `/all` `/:id/tasks` `/secret-status` `/tags` |
| 日志 | GET `/api/v1/logs?task_id=&page=&page_size=`；GET `/logs/:id`；GET `/logs/sse`（流式）；POST `/logs/clear`；DELETE `/logs/:id` |
| 依赖 | GET `/api/v1/dependencies`（无查询参数，官方文档口径）；POST 创建/`install` `/uninstall/:id` `/reinstall/:id` `/reinstall-all` |
| 设置 | `/api/v1/settings/*`（password/site/paths/scheduler/about/changelog/loginlogs/backup/restore/:section） |
| 终端 | GET `/api/v1/terminal/ws`（WebSocket）`/terminal/cmds` |
| 系统 | GET `/api/v1/monitor` `/monitor/sse`；`/ws/events` |

## 错误处理
- 业务错误统一 `{code: 4xx/500, msg: "..."}`（HTTP 状态可能同时为 500）
- 文件不存在类错误示例：`open /app/data/scripts/xxx: no such file or directory` —— 客户端需容错（demo 站列表接口与磁盘可能不一致）
