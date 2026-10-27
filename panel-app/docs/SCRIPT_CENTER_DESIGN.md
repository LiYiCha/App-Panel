# 本地脚本中心 & 暂停 & 运行记录 & GitHub 加速修复 —— 设计文档

> 状态：设计定稿，待开发（2026-10-28）
> 范围：四项改进，全部经实机排查/实测后确定方案

---

## 0. 背景与问题清单

| # | 用户反馈 | 排查结论 |
|---|---------|---------|
| 1 | 缺少 GitHub 的代理加速 | `AppUpdateManager.GITHUB_API_FALLBACK = "https://ghproxy.com/"` **域名已失效**（实测连接失败）；直连 api.github.com 国内常超时 → 检查更新经常完全失败。实测 **gh-proxy.com 可用**（API 代理返回 200）；ghfast.top/ghproxy.net 仅代理文件下载，不支持 API（403） |
| 2 | 没有暂停 | 现状只有「停止」。Python 可实现真正暂停/继续（settrace 轮询点阻塞等待）；**JS 同步执行技术上无法暂停**（原生调用占用线程，只能停止），设计如实区分 |
| 3 | 缺日志查看管理 | `files/run_logs/` 保留 50 个日志文件，但**无任何管理 UI**：不能看历史列表、不能查看旧输出、不能删除 |
| 4 | 功能分散 | 本地脚本域拆成 4 个平级路由（local_scripts / local_editor / local_console / local_deps），入口散、跳转层级深 |

---

## 1. 方案一：GitHub 代理加速修复（bug fix，改动最小）

### 1.1 现状代码
`AppUpdateManager.kt`：
```
GITHUB_API_BASE     = "https://api.github.com/"      // 主链路
GITHUB_API_FALLBACK = "https://ghproxy.com/"          // 已失效 ← 根因
```

### 1.2 改动
| 项 | 改动 |
|----|------|
| 备用源 | `ghproxy.com` → `https://gh-proxy.com/`（2026-10-27 实测 API 代理 200 OK） |
| 请求顺序 | 直连 api.github.com → 失败（异常或非 2xx）→ gh-proxy.com。与现有 try/catch 结构一致，只换 URL |
| 下载加速 | 不动。`MirrorManager.applyUpdate`（ghfast 前缀）对 Release 文件下载有效，已实机验证链路正确 |

### 1.3 可选增强（一并做，成本极低）
gh-proxy.com 与 ghfast.top 均可能随时间失效 → 在 `MirrorManager` 中把 API 备用源定义为常量并加注释「失效时更换」，不做 UI 配置（避免过度设计）。

---

## 2. 方案二：运行暂停 / 继续

### 2.1 技术边界（重要，需如实落地）

| 语言 | 暂停 | 继续 | 原理 |
|------|------|------|------|
| Python | ✅ 真暂停 | ✅ | `py_runner.py` 的 `sys.settrace` 逐行回调中检查 `pause_flag`，置位时循环 `time.sleep(0.1)` 等待（同时优先检查 stop，保证暂停中可停止） |
| JS | ❌ 同步脚本不可暂停 | — | QuickJS `evaluate()` 是一次性的原生调用，执行期间 JS 占死服务线程，Kotlin 无法插手；`ScriptJsEngine` 无 job 循环模型。**JS 运行中只提供「停止」**（停止对异步脚本有效，同步死循环靠看门狗上报） |

> JS 若强行支持暂停需把引擎改为「job 循环 + evaluate 分片」模型（重写 QuickJsEngine 执行流），风险高收益低，本期不做。UI 上 JS 运行时暂停按钮不显示，仅显示停止。

### 2.2 数据层改动

**`ScriptRunBus`（core-script）**
```kotlin
enum class RunStatus { RUNNING, PAUSED, SUCCESS, FAILED, STOPPED }   // 新增 PAUSED
```

**新增 `PauseFlag`（core-script，与 StopFlag 同构）**
```kotlin
class PauseFlag {
    private val paused = AtomicBoolean(false)
    fun pause() / resume() / isPaused()
}
```

**`py_runner.py`**：`run_script(...)` 增加 `pause_flag` 参数
```python
def _trace(frame, event, arg):
    if stop_flag.isStopped():
        raise SystemExit(130)
    while pause_flag.isPaused() and not stop_flag.isStopped():
        time.sleep(0.1)          # 行间暂停；settrace 每行都会回到这里
    return _trace
```
- 暂停期间 stdout 缓冲中的行仍会继续 flush（write 在 print 返回后回调），属可接受行为
- 暂停不重置 30min 超时看门狗计时 → **暂停时间计入总时长**（设计取舍：避免暂停被滥用于无限挂起；UI 上暂停时显示「已暂停（计时继续）」）

**`ScriptRunnerService`**：
- Intent 增加 ACTION_PAUSE / ACTION_RESUME（复用现有 onStartCommand 分发模式）
- `onRunStarted` 后总线推 `RUNNING`；pause/resume 时推 `PAUSED/RUNNING` 状态变更（新增 `ScriptRunBus.onStatusChanged(runId, status)`）
- 停止逻辑不变：暂停中收到 stop，py 侧 trace 循环检测 stop 优先退出（130）

### 2.3 UI 改动（LocalRunConsoleScreen 控制台）

运行中（live）底部操作条：
- Python：`[暂停]` ⇄ `[继续]` + `[停止]`（状态色：暂停=tertiary，停止=error）
- JS：仅 `[停止]`
- 非运行中不显示操作条（现状保持）

### 2.4 涉及文件
| 文件 | 改动 |
|------|------|
| `core-script/.../ScriptRunBus.kt` | RunStatus+PAUSED、onStatusChanged |
| `core-script/.../engine/ScriptContracts.kt` | PauseFlag |
| `core-script/.../engine/python/PythonEngine.kt` | 持有 PauseFlag、透传 py_runner、pause()/resume() |
| `core-script/src/main/python/py_runner.py` | _trace 暂停等待 |
| `core-script/.../runner/ScriptRunnerService.kt` | PAUSE/RESUME action、状态推送 |
| `app/full/.../LocalRunConsoleScreen.kt` | 操作条按钮 |

---

## 3. 方案三：运行记录页（历史日志管理）

### 3.1 数据源与元数据

现状：`files/run_logs/{runId}.log`，**runId = UUID**（无时间/语言信息），行格式 `[HH:mm:ss] [err] text`，首行 `== 运行开始 {脚本名} [{语言}] ==`。

**runId 格式升级（本设计一并做）**：
```
runId = yyyyMMdd_HHmmss_<uuid8>      // 如 20261028_143025_a1b2c3d4
```
- 列表排序/展示直接用文件名，不必逐文件读内容
- **旧文件兼容**：无前缀的 UUID 文件按 `lastModified` 排序、按首行解析元数据

### 3.2 新增 `RunLogStore`（core-script，纯文件操作）
```kotlin
data class RunLogEntry(
    val runId: String,        // = 文件名去 .log
    val startedAt: Long,      // 文件名解析或 lastModified
    val scriptName: String,   // 首行解析（旧文件）
    val lang: ScriptLang?,    // 首行解析（旧文件）
    val status: RunStatus?,   // 尾部扫描「成功/失败/已停止」关键字
    val sizeBytes: Long,
)

class RunLogStore(dir: File) {
    fun list(): List<RunLogEntry>                  // 按时间倒序
    fun read(runId: String): List<ConsoleLine>     // 流式解析（复用 LocalRunConsoleScreen 回放解析逻辑，下沉到 core-script 共用）
    fun delete(runId: String)
    fun clearAll()
}
```

### 3.3 UI：`RunHistoryScreen`（full 源集）
```
┌ 运行记录 ────────────────── [清空] ┐
│ ┌────────────────────────────────┐ │
│ │ hello.py  Python      ✅ 成功  │ │  ← 点击进入 local_console/{runId} 回放
│ │ 10-28 14:30 · 2.1 KB           │ │
│ ├────────────────────────────────┤ │
│ │ hello.js  JavaScript  ❌ 失败  │ │
│ │ 10-28 13:02 · 0.8 KB    [删除] │ │  ← 长按或右侧删除图标（确认框）
│ └────────────────────────────────┘ │
│ 空态：暂无运行记录                   │
└────────────────────────────────────┘
```
- 清空全部需确认框；删除单条直接删（有误删成本低）
- 状态解析失败的旧日志显示「—」

### 3.4 涉及文件
| 文件 | 改动 |
|------|------|
| `core-script/.../runner/ScriptRunnerService.kt` | runId 生成改时间前缀格式 |
| `core-script/.../store/RunLogStore.kt`（新建） | 列表/解析/删除/清空 |
| `app/full/.../RunHistoryScreen.kt`（新建） | 列表页 UI |
| `LocalRunConsoleScreen.kt` | 回放解析逻辑改调 RunLogStore.read（去重） |

---

## 4. 方案四：脚本中心单页整合

### 4.1 目标结构
设置页入口「本地脚本（JS / Python）」→ **ScriptCenterScreen（单页三 Tab）**：

```
┌ 本地脚本 ──────────── [⊕新建] [⇪导入] [⏱记录] ┐
│ ┌─────────┬─────────┬─────────┐              │
│ │  脚本   │  依赖   │ 运行记录 │   ← Tab      │
│ └─────────┴─────────┴─────────┘              │
│  Tab1: 现有 LocalScriptsScreen 列表内容        │
│  Tab2: 现有 LocalDepsScreen 内容              │
│  Tab3: RunHistoryScreen 内容（见方案三）       │
└──────────────────────────────────────────────┘
```

### 4.2 路由与导航调整
| 变化 | 内容 |
|------|------|
| 保留 | `local_editor/{name}`（编辑器）、`local_console/{runId}`（控制台）——从中心页跳入，层级不变 |
| 新增 | `local_center`（单页三 Tab）；`local_center?tab=history` 支持直达运行记录 Tab |
| 移除 | 独立路由 `local_scripts`、`local_deps`（内容并入中心页 Tab；如有外部跳转点一并改） |
| Hilt | `DefaultLocalScriptFeature.installRoutes` 注册表同步改（standard 变体不动） |

### 4.3 重构方式（最小侵入）
- 现有 `LocalScriptsScreen` / `LocalDepsScreen` 拆出内容区 Composable（如 `ScriptsTabContent` / `DepsTabContent`），Scaffold/TopBar 留给中心页统一
- Tab 用 Material3 `TabRow` + `HorizontalPager`（保留各 Tab 滚动位置，惯性体验）
- 设置页入口文案与跳转目标改为 `local_center`，其余不变

### 4.4 涉及文件
| 文件 | 改动 |
|------|------|
| `app/full/.../ScriptCenterScreen.kt`（新建） | Tab 容器 |
| `LocalScriptsScreen.kt` / `LocalDepsScreen.kt` | 拆内容组件，原独立页面入口移除 |
| `RunHistoryScreen.kt` | 作为 Tab3 内容组件复用（同样支持独立打开，从中心页顶栏时钟图标） |
| `DefaultLocalScriptFeature.kt` / `MainActivity.kt` | 路由注册与跳转点更新 |
| `SettingsScreen.kt` | 入口跳转目标改 `local_center` |

---

## 5. 实施清单（建议顺序）

1. **方案一** GitHub 加速（独立、零风险，先提交）
2. **方案二** 暂停/继续（core-script 引擎层 → 服务层 → UI）
3. **方案三** runId 格式 + RunLogStore + RunHistoryScreen
4. **方案四** 脚本中心整合（路由动最大，最后做，避免与前三冲突）
5. 回归：`assembleStandardDebug assembleFullDebug assembleFullRelease` 全绿

## 6. 验证清单（实机，模拟器 16KB 设备）

- [ ] 检查更新：断网/直连超时时走 gh-proxy.com 能拿到 release
- [ ] Python：运行→暂停（输出停住）→继续（输出恢复）→停止（暂停中可停）
- [ ] JS：运行中仅显示停止；停止生效（异步）/看门狗上报（同步死循环）
- [ ] 运行记录：跑 3 个脚本后列表 3 条（时间倒序、语言/状态正确）、点击回放内容完整、删除/清空生效
- [ ] 旧 UUID 日志文件兼容展示（首行解析名称、lastModified 排序）
- [ ] 脚本中心三 Tab 切换、编辑器/控制台跳转往返正常、standard 变体无脚本入口
- [ ] 暂停状态下 30min 看门狗仍会触发（UI 有「计时继续」提示）
