# 青龙面板 API 参考（源码取证版）

> 来源：`qinglong` 仓库源码（本地 `temp/qinglong`，back/ 为后端，扁平结构 `back/api/*.ts` 路由 + `back/services/*.ts` 实现）
> 在线文档：https://qinglong.online/api/user（两步验证等用户接口）
> 实测版本：v2.20.2

## 响应格式
- 成功：`{ code: 200, data: ... }`
- 业务错误：HTTP 200 + `{code: 4xx/500, message: "..."}`
- 鉴权：登录返回 JWT（HS384），后续请求 `Authorization: Bearer <token>`；token 有效期 60d（登录用户）/20d（OpenAPI）

## 用户与两步验证（back/api/user.ts + back/services/user.ts）

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/user/login` | body `{username, password}`；限速 15min/100 次 |
| PUT | `/api/user/two-factor/login` | 两步验证登录，body `{code, username, password}`（**账号密码重新提交**）；限速 15min/20 次 |
| POST | `/api/user/logout` | 登出 |
| GET/PUT | `/api/user/` | 获取/更新用户信息（GET 返回 `twoFactorActivated`） |
| GET | `/api/user/two-factor/init` | 初始化 2FA（返回二维码/密钥） |
| PUT | `/api/user/two-factor/active` | 激活 2FA `{code}` |
| PUT | `/api/user/two-factor/deactivate` | 停用 2FA |
| GET | `/api/user/login-log` | 登录日志 |
| GET/PUT/DELETE | `/api/user/ip-blacklist` | IP 黑名单（PUT/DELETE body `{"ip":"1.2.3.4"}`，支持单 IPv4/IPv6，不支持 CIDR） |
| GET/PUT | `/api/user/notification` | 通知设置 |
| PUT | `/api/user/init` `/notification/init` | 初始化 |
| PUT | `/api/user/avatar` | 头像（multipart，字段 `avatar`，≤5MiB） |

### 两步验证状态机（源码确认）
| 场景 | 响应 |
|---|---|
| 开启 2FA 后普通登录（未带验证码） | `{code: 420, message: ''}`，服务端进入 `isTwoFactorChecking` 状态，**5 分钟窗口** |
| 两步验证登录成功 | `{code: 200, data: {token, lastip, lastaddr, lastlogon, retries, platform}}`（无 expiration/token_type） |
| 验证码错误（含 30s 时间步重复使用） | `{code: 430, message: '验证失败'}` |
| 失败次数过多（节流） | `{code: 410, message: '失败次数过多，请稍后重试'/带 %s 秒}` |

## 定时任务（back/api/cron.ts）
- `GET /api/crons`（列表，分页 `{data:{data:[],total}}`；**响应不含下次运行时间字段**——客户端需本地按 cron 计算）
- CRUD：POST/PUT/DELETE `/api/crons`；`GET /api/crons/:id`
- 日志：`GET /api/crons/:id/log?limit=&tail=&offset=`（实时轮询/续传）
- 运行实例：`GET /api/crons/:id/instances`；批量操作与启停见 `back/api/cron.ts`

## 脚本（back/api/script.ts）
- `GET /api/scripts`（**支持 `path` 查询参数**：按目录懒加载子列表；顶层目录不含 children）
- `GET /api/scripts/:file`；POST/PUT/DELETE 及下载

## 其他
- 依赖：`GET /api/dependencies`（无查询参数，文档口径）；POST 创建/安装、PUT 重装、DELETE 卸载
- 环境变量：`GET /api/envs`；CRUD + 启停/移动（以 `back/api/env.ts` 为准）
- 系统信息：`GET /api/system`（version/branch）、`GET /api/system/config`
- 订阅：`/api/subscriptions` CRUD
- 注意：**青龙没有 `/api/dashboard/*` 路由**（对本 App 调用的 overview/top-count/top-time/labels/trend/system 而言不存在；未知 API 路径由前端 SPA 兜底返回 index.html，即 HTTP 200 + HTML）

## 历史教训（禁止再犯）
1. `/api/dashboard/*` 是白虎专属，青龙禁止请求（SPA 兜底 HTML 假 200）。
2. 青龙 crons 响应无 next run 字段——下次运行时间必须本地按 cron 计算（App 内 `CronNextRun`）。
3. 青龙 cron 为 **6 域**（秒 分 时 日 月 周），解析时需丢弃秒域。
4. 白虎 `files/tree` 无查询参数（勿再猜 `?path=`），全量树。
