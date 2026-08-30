# 青龙面板 (Qinglong) 与 白虎面板 (Baihu) 官方全量 API 规范参考手册

本参考手册直接提取自本地官方代码仓库：
- **青龙面板源码**：`d:\exploitation\Panel-app\qinglong\back\api\`
- **白虎面板源码**：`d:\exploitation\Panel-app\baihu-panel\internal\router\api_routes.go`

---

## 目录
1. [认证与凭据系统 (Auth)](#一认证与凭据系统-auth)
2. [定时任务系统 (Tasks / Crons)](#二定时任务系统-tasks--crons)
3. [环境变量系统 (Envs)](#三环境变量系统-envs)
4. [脚本与文件管理 (Scripts / Files)](#四脚本与文件管理-scripts--files)
5. [运行日志系统 (Logs)](#五运行日志系统-logs)
6. [环境依赖管理 (Dependencies)](#六环境依赖管理-dependencies)
7. [仓库订阅系统 (Subscriptions)](#七仓库订阅系统-subscriptions)

---

## 一、认证与凭据系统 (Auth)

### 1. 青龙面板 (Qinglong v2.15+)
- **OpenAPI 凭据获取**：
  * **请求**：`GET /open/auth/token?client_id=<cid>&client_secret=<csec>`（或 `/api/auth/token`）
  * **响应**：
    ```json
    { "code": 200, "data": { "token": "...", "token_type": "Bearer", "expiration": 1724930000 } }
    ```
- **用户直接登录 (Web / Client)**：
  * **请求**：`POST /api/user/login`
  * **Body**：`{ "username": "admin", "password": "password" }`
  * **响应**：`{ "code": 200, "data": { "token": "..." } }`
- **鉴权 Header**：所有受保护请求必须携带 `Authorization: Bearer <token>`。

### 2. 白虎面板 (Baihu Panel)
- **用户登录**：
  * **请求**：`POST /api/v1/auth/login`
  * **Body**：`{ "username": "admin", "password": "password" }`
  * **机制**：服务端在 HTTP 响应头通过 `Set-Cookie: BHToken=<jwt>; Path=/; HttpOnly` 下发 Token。
- **鉴权 Header / Cookie**：
  * 必须携带 Cookie: `Cookie: BHToken=<token>`；
  * 或携带 Header: `Authorization: Bearer <token>`（内部互联与远程控制）。
- **两步验证 (2FA/OTP)**：
  * `GET /api/v1/auth/otp/status`
  * `POST /api/v1/auth/otp/generate`
  * `POST /api/v1/auth/otp/enable`
  * `POST /api/v1/auth/otp/disable`

---

## 二、定时任务系统 (Tasks / Crons)

### 1. 青龙面板 (`/api/crons`)
| 操作 | 方法与路由 | 参数 / 请求体说明 |
| :--- | :--- | :--- |
| **获取任务列表** | `GET /api/crons?searchValue=` | 包含名称、命令、Cron、状态（0: 运行中, 1: 空闲） |
| **新建任务** | `POST /api/crons` | `{ "name": "...", "command": "...", "schedule": "0 8 * * *", "labels": [] }` |
| **更新任务** | `PUT /api/crons` | `{ "id": 1, "name": "...", "command": "...", "schedule": "..." }` |
| **删除任务** | `DELETE /api/crons` | `[ 1, 2, 3 ]` (ID 数组) |
| **运行任务** | `PUT /api/crons/run` | `[ 1, 2 ]` (ID 数组) |
| **停止运行** | `PUT /api/crons/stop` | `[ 1, 2 ]` (ID 数组) |
| **启用任务** | `PUT /api/crons/enable` | `[ 1, 2 ]` (ID 数组) |
| **禁用任务** | `PUT /api/crons/disable` | `[ 1, 2 ]` (ID 数组) |
| **置顶/取消置顶** | `PUT /api/crons/pin` / `unpin` | `[ 1, 2 ]` (ID 数组) |
| **获取任务历史日志** | `GET /api/crons/:id/log` | 返回任务执行日志文本 |

### 2. 白虎面板 (`/api/v1/tasks` & `/api/v1/execute`)
| 操作 | 方法与路由 | 参数 / 请求体说明 |
| :--- | :--- | :--- |
| **获取任务列表** | `GET /api/v1/tasks?page=1&page_size=100&name=` | 返回 `{ code: 200, data: { data: [...], total, page } }` |
| **新建任务** | `POST /api/v1/tasks` | `{ "name": "...", "command": "...", "schedule": "...", "timeout": 30 }` |
| **获取单任务详情** | `GET /api/v1/tasks/:id` | 返回任务元数据与上次运行信息 |
| **修改任务** | `PUT /api/v1/tasks/:id` | `{ "name": "...", "command": "...", "schedule": "...", "enabled": true }` |
| **删除任务** | `DELETE /api/v1/tasks/:id` | 单个删除 |
| **批量删除** | `POST /api/v1/tasks/batch-delete` | `{ "ids": ["id1", "id2"] }` |
| **触发执行** | `POST /api/v1/execute/task/:id` | 立即触发后台执行 |
| **停止运行** | `POST /api/v1/tasks/stop/:logID` | 终止正在运行的进程 |

---

## 三、环境变量系统 (Envs)

### 1. 青龙面板 (`/api/envs`)
- **获取环境变量**：`GET /api/envs?searchValue=`
  * 返回数组：`[ { "id": 1, "name": "JD_COOKIE", "value": "...", "remarks": "...", "status": 0 } ]`
  * **状态规范**：`status: 0` 为**已启用**；`status: 1` 为**已禁用**。
- **批量新建变量**：`POST /api/envs`
  * **注意**：必须为数组 `[ { "name": "...", "value": "...", "remarks": "..." } ]`
- **更新变量**：`PUT /api/envs`
  * 单对象：`{ "id": 1, "name": "...", "value": "...", "remarks": "..." }`
- **删除变量**：`DELETE /api/envs`，Body 为 `[ 1, 2 ]`
- **启用变量**：`PUT /api/envs/enable`，Body 为 `[ 1, 2 ]`
- **禁用变量**：`PUT /api/envs/disable`，Body 为 `[ 1, 2 ]`

### 2. 白虎面板 (`/api/v1/env`)
- **获取环境变量**：`GET /api/v1/env?page=1&page_size=100&name=`
  * 状态属性：`enabled: Boolean` (true: 已启用, false: 已禁用)
- **新建变量**：`POST /api/v1/env`
  * 单对象：`{ "name": "...", "value": "...", "remark": "...", "enabled": true }`
- **更新变量**：`PUT /api/v1/env/:id`
- **删除变量**：`DELETE /api/v1/env/:id`
- **批量保存**：`POST /api/v1/env/bulk_save`

---

## 四、脚本与文件管理 (Scripts / Files)

### 1. 青龙面板 (`/api/scripts`)
- **脚本树获取**：`GET /api/scripts?path=`
- **脚本内容读取**：`GET /api/scripts/:file`
- **新建/保存脚本**：`POST /api/scripts` 与 `PUT /api/scripts`
  * `{ "filename": "test.py", "content": "print('hello')", "path": "" }`
- **删除脚本**：`DELETE /api/scripts`
- **上传脚本文件**：`POST /api/scripts/upload`（`multipart/form-data`，字段名 `file`）

### 2. 白虎面板 (`/api/v1/scripts` & `/api/v1/files`)
- **脚本列表**：`GET /api/v1/scripts`
- **创建脚本**：`POST /api/v1/scripts` (`{ "name": "...", "content": "..." }`)
- **文件树**：`GET /api/v1/files/tree`
- **读取文件**：`GET /api/v1/files/content?path=`
- **保存文件**：`POST /api/v1/files/content` (`{ "path": "...", "content": "..." }`)
- **上传文件**：`POST /api/v1/files/upload` 与 `/uploadfiles`

---

## 五、运行日志系统 (Logs)

### 1. 青龙面板
- **实时/最新任务日志**：`GET /api/crons/:id/log`
- **日志直接拉取**：`/api/logs/...`

### 2. 白虎面板
- **任务日志历史列表**：`GET /api/v1/logs?task_id=:id&page=1&page_size=20`
- **单条日志详情**：`GET /api/v1/logs/:id`
- **SSE 流式实时日志**：`GET /api/v1/logs/sse`
- **清空日志**：`POST /api/v1/logs/clear`

---

## 六、环境依赖管理 (Dependencies)

### 1. 青龙面板 (`/api/dependencies`)
- **获取依赖列表**：`GET /api/dependencies?searchValue=&type=`
  * `type` 类型枚举：`0: Node.js (npm)`, `1: Python3 (pip)`, `2: Linux`
- **安装依赖**：`POST /api/dependencies`
  * Body 为数组：`[ { "name": "requests", "type": 1, "remark": "网络库" } ]`
- **卸载依赖**：`DELETE /api/dependencies`
  * Body 为 ID 数组：`[ 101, 102 ]`
- **重新安装**：`PUT /api/dependencies/re-install`

### 2. 白虎面板 (`/api/v1/dependencies`)
- **获取依赖**：`GET /api/v1/dependencies?language=`
- **安装依赖**：`POST /api/v1/dependencies`
  * Body: `{ "name": "...", "version": "...", "language": "python3", "remark": "..." }`
- **卸载依赖**：`DELETE /api/v1/dependencies/:id`

---

## 七、仓库订阅系统 (Subscriptions)

### 1. 青龙面板 (`/api/subscriptions`)
- **订阅列表**：`GET /api/subscriptions`
- **新增订阅**：`POST /api/subscriptions`
  * `{ "name": "...", "url": "https://github.com/.../repo.git", "type": "public-repo", "schedule": "0 0 * * *" }`
- **拉取命令标准**：`ql repo <url> <path> <blacklist> <dependence> <branch>`

### 2. 白虎面板
- **仓库同步**：`POST /api/v1/internal/tasks/sync-repo-status`
- **Git 订阅任务**：`POST /api/v1/tasks` 配置 `git clone` 或 `git pull` 指令
