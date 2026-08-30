# 青龙 (Qinglong) 与 白虎 (Baihu) 面板真实 API 请求与响应全景矩阵及适配文档

本文档基于本地青龙后端源码（`d:/exploitation/Panel-app/qinglong/back/api/*.ts`）与白虎面板后端源码（`d:/exploitation/Panel-app/baihu-panel/internal/controllers/*.go`），以及真实网络请求/响应抓包记录，进行逐项 API 审查、对比与客户端补全修复。

---

## 1. 核心模块真实 API 请求与响应比对

### 1.1 定时任务 (Tasks / Crons)

#### 青龙面板 (Qinglong)
- **获取任务列表**: `GET /api/crons?searchValue=&page=1&size=100`
  - **响应格式** (v2.15+ 为分页结构，老版本为纯数组，客户端已双重自适应)：
    ```json
    {
      "code": 200,
      "data": {
        "data": [
          {
            "id": 1,
            "name": "测试任务",
            "command": "task test.js",
            "schedule": "0 0 * * *",
            "status": 0, // 0: 就绪/停止, 1: 队列中, 2: 运行中
            "isDisabled": 0, // 0: 启用, 1: 禁用
            "isPinned": 1, // 0: 未置顶, 1: 置顶
            "extra_schedules": [],
            "labels": ["每日"],
            "last_running_time": 3,
            "last_execution_time": 1788065000,
            "sub_id": null
          }
        ],
        "total": 1
      }
    }
    ```
- **运行任务**: `PUT /api/crons/run`，请求体：`[1, 2]`
- **停止任务**: `PUT /api/crons/stop`，请求体：`[1, 2]`
- **启用任务**: `PUT /api/crons/enable`，请求体：`[1, 2]`
- **禁用任务**: `PUT /api/crons/disable`，请求体：`[1, 2]`
- **置顶任务**: `PUT /api/crons/pin`，请求体：`[1, 2]`
- **取消置顶**: `PUT /api/crons/unpin`，请求体：`[1, 2]`
- **删除任务**: `DELETE /api/crons` (HTTP DELETE 带 Body)，请求体：`[1, 2]`
- **历次执行实例**: `GET /api/crons/:id/instances`
  - **响应结构**:
    ```json
    {
      "code": 200,
      "data": [
        {
          "id": 105,
          "cron_id": 1,
          "pid": 8921,
          "log_path": "test/2026-08-30-12-00-00.log",
          "started_at": 1788065000,
          "finished_at": 1788065003,
          "status": 1, // 0: running, 1: finished, 2: stopped, 3: error
          "exit_code": 0
        }
      ]
    }
    ```
- **获取最新实时任务日志**: `GET /api/crons/:id/log`
- **查看特定日志文件详情**: `GET /api/logs/detail?path=...&file=...`

#### 白虎面板 (Baihu)
- **获取任务列表**: `GET /api/v1/tasks?name=&page=1&page_size=100`
  - **响应格式**:
    ```json
    {
      "code": 200,
      "msg": "success",
      "data": {
        "data": [
          {
            "id": "673c...",
            "name": "白虎测试任务",
            "command": "python3 main.py",
            "schedule": "0 */2 * * *",
            "running_status": "idle", // idle, running
            "enabled": true,
            "pin_type": "top", // top 为置顶
            "timeout": 3600,
            "remark": "备注"
          }
        ],
        "total": 1,
        "page": 1,
        "page_size": 100
      }
    }
    ```
- **运行任务**: `POST /api/v1/execute/task/:id`
- **停止任务**: `POST /api/v1/tasks/stop/:logID`
- **单项更新/切换启停/置顶**: `PUT /api/v1/tasks/:id`
  - 请求体：`{"enabled": true, "pin_type": "top"}`
- **批量删除任务**: `POST /api/v1/tasks/batch-delete`
  - 请求体：`{"ids": ["id1", "id2"]}`
- **任务执行日志列表**: `GET /api/v1/logs?task_id=:id&page=1&page_size=20`
- **任务日志详情**: `GET /api/v1/logs/:id`

---

### 1.2 环境变量 (Environment Variables)

#### 青龙面板 (Qinglong)
- **获取列表**: `GET /api/envs?searchValue=`
- **新建变量**: `POST /api/envs`，**注意：请求体为 JSON 数组**：
  ```json
  [
    {
      "name": "JD_COOKIE",
      "value": "pt_key=AA...;pt_pin=bb;",
      "remarks": "账号1"
    }
  ]
  ```
- **更新变量**: `PUT /api/envs`，请求体：`{"id": 1, "name": "...", "value": "...", "remarks": "..."}`
- **批量删除**: `DELETE /api/envs`，请求体：`[1, 2]`
- **批量启用**: `PUT /api/envs/enable`，请求体：`[1, 2]`
- **批量禁用**: `PUT /api/envs/disable`，请求体：`[1, 2]`

#### 白虎面板 (Baihu)
- **获取列表**: `GET /api/v1/env?name=&page=1&page_size=100`
- **新建变量**: `POST /api/v1/env`，请求体：`{"name": "...", "value": "...", "remark": "...", "enabled": true}`
- **更新变量**: `PUT /api/v1/env/:id`
- **删除变量**: `DELETE /api/v1/env/:id`

---

### 1.3 脚本与文件系统 (Scripts / Files)

#### 青龙面板 (Qinglong)
- **获取文件树/根目录**: `GET /api/scripts` (支持 `?file=subfolder` 展开子目录)
- **读取脚本内容**: `GET /api/scripts/:filename` (子目录采用 `GET /api/scripts?path=dir&file=name.js`)
- **保存脚本**: `POST /api/scripts`，请求体：`{"filename": "...", "content": "...", "path": "..."}`
- **删除脚本**: `DELETE /api/scripts`，请求体：`{"filename": "...", "path": "..."}`

#### 白虎面板 (Baihu)
- **获取文件树**: `GET /api/v1/files/tree`
- **读取文件内容**: `GET /api/v1/files/content?path=scripts/test.py`
- **保存文件内容**: `POST /api/v1/files/content`，请求体：`{"path": "...", "content": "..."}`
- **新建文件/目录**: `POST /api/v1/files/create`，请求体：`{"path": "...", "isDir": false}`
- **删除文件/目录**: `POST /api/v1/files/delete`，请求体：`{"path": "..."}`

---

### 1.4 依赖管理 (Dependencies)

#### 青龙面板 (Qinglong)
- **列表**: `GET /api/dependencies?type=nodejs` (支持 `nodejs`, `python3`, `linux`)
- **安装依赖**: `POST /api/dependencies`，请求体：`[{"name": "requests", "type": "python3"}]`
- **卸载依赖**: `DELETE /api/dependencies` (HTTP DELETE 带 Body)，**注意：请求体为整数 ID 数组 `[1, 2]`**
- **依赖日志**: `GET /api/dependencies/:id`

#### 白虎面板 (Baihu)
- **列表**: `GET /api/v1/deps?language=python3`
- **安装依赖**: `POST /api/v1/deps`，请求体：`{"name": "requests", "language": "python3"}`
- **卸载依赖**: `POST /api/v1/deps/uninstall/:id`
- **删除依赖记录**: `DELETE /api/v1/deps/:id`

---

### 1.5 审计中心与系统监控 (Audit & Monitor)

#### 青龙面板 (Qinglong)
- **登录审计日志**: `GET /api/user/login-log`
  - 字段包含：`ip`, `address`, `status` (0: 成功), `createdAt`
- **服务端日志目录树**: `GET /api/logs`
- **服务端具体日志内容**: `GET /api/logs/detail?path=...&file=...`
- **系统监控**: `GET /api/dashboard/system`
  - 解析 `cpu.cpuUsage` 与 `memory.total`/`memory.free` 计算系统负载

#### 白虎面板 (Baihu)
- **登录审计日志**: `GET /api/v1/settings/loginlogs?page=1&page_size=50`
  - 字段包含：`ip`, `user_agent`, `status`, `message`, `created_at`
- **任务日志中心**: `GET /api/v1/logs?page=1&page_size=50`
- **日志详情**: `GET /api/v1/logs/:id`
- **系统监控**: `GET /api/v1/monitor`
  - 直接读取 `host.cpu_percent` 与 `host.mem_used`

---

## 2. 本次对照检查发现的问题与针对性修复记录

| 审查项 | 发现的潜在问题 / 协议不一致 | 修复措施与文件定位 |
| :--- | :--- | :--- |
| **青龙环境变量启用/禁用路由错误** | `QinglongV15Adapter.kt` 中 `toggleEnv` 曾误调用 `api.enableCrons` / `api.disableCrons`，导致切换变量启停时被青龙调度器报错或忽略。 | 1. 在 [`QinglongV15Api.kt`](file:///d:/exploitation/Panel-app/panel-app/app/src/main/java/com/panel/app/data/remote/api/QinglongV15Api.kt) 中补充补全 `PUT api/envs/enable` 与 `PUT api/envs/disable` 接口。<br>2. 在 [`QinglongV15Adapter.kt`](file:///d:/exploitation/Panel-app/panel-app/app/src/main/java/com/panel/app/data/adapter/QinglongV15Adapter.kt) 中修正 `toggleEnv` 绑定为真正的变量启停接口。 |
| **青龙任务执行历史时间与日志关联** | `QlCronInstanceItem` 此前仅接收 `created_at` 字符串，青龙后端实际存储的为 `started_at` (Unix 秒戳)、`finished_at` 以及 `log_path`，导致历史日志无法精准调取。 | 1. 在 `QlCronInstanceItem` 中补齐 `started_at`, `finished_at`, `log_path`, `exit_code`。<br>2. 在 `TaskInstanceRecord` 中增加 `logPath` 属性。<br>3. 在 [`TaskDetailScreen.kt`](file:///d:/exploitation/Panel-app/panel-app/app/src/main/java/com/panel/app/ui/screens/TaskDetailScreen.kt) 中点击执行历史时，若包含 `logPath` 则自动从服务端精确读取当次日志文件。 |
| **白虎任务批量删除未对齐专用路由** | 白虎后端提供了高性能专用接口 `POST /api/v1/tasks/batch-delete`，之前客户端使用循环单项删除。 | 在 [`BaihuApi.kt`](file:///d:/exploitation/Panel-app/panel-app/app/src/main/java/com/panel/app/data/remote/api/BaihuApi.kt) 与 [`BaihuPanelAdapter.kt`](file:///d:/exploitation/Panel-app/panel-app/app/src/main/java/com/panel/app/data/adapter/BaihuPanelAdapter.kt) 中正式接入 `POST /api/v1/tasks/batch-delete`。 |
| **白虎任务置顶协议对齐** | 白虎通过 `pin_type: "top"` 或 `"time"` 进行任务置顶控制，原适配器为空实现。 | 在 `BaihuUpdateTaskReq` 中加入 `pin_type` 字段，并在 `pinTask` 中通过 `PUT /api/v1/tasks/:id` 提交置顶。 |
| **白虎登录日志与服务端日志中心缺失** | 原 `BaihuPanelAdapter.kt` 中 `getLoginLogs`、`getLogsTree`、`getLogDetail` 均为空桩代码。 | 1. 在 `BaihuApi.kt` 中新增 `GET api/v1/settings/loginlogs`。<br>2. 在 `BaihuPanelAdapter.kt` 中打通登录审计日志列表映射。<br>3. 打通白虎 `GET /api/v1/logs` 与 `GET /api/v1/logs/:id` 作为服务端日志树和详情展示。 |

