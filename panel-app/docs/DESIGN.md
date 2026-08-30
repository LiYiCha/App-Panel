# Panel Hub - 跨平台多面板客户端 (白虎 / 青龙 v2.10 & v2.15+) 全量架构与设计终极规范文档 (DESIGN.md)

> **版本**：v1.4.0 (复杂变量解析强化版)  
> **更新时间**：2026-08-29  
> **文档说明**：本文档包含 Android 原生工程架构、Go 守护进程高保活防杀死机制、安卓 10+ W^X 执行绕过方案、国内定制 ROM 电池优化对策、青龙 (v2.10 / v2.15+) 与白虎面板的精确 API 对齐映射表、多层级文件夹脚本树算法、**通用多格式环境变量解析算法 (支持换行/@/&#/URL Decode/export)**、以及 Phase 4 的 `zip.gz` 终极部署方案。

---

## 目录
1. [软件定位与核心原则](#1-软件定位与核心原则)
2. [方案隐患剖析与针对性解决策 (Risk Assessment)](#2-方案隐患剖析与针对性解决策-risk-assessment)
3. [复杂环境变量多格式智能解析算法与实体解码](#3-复杂环境变量多格式智能解析算法与实体解码)
   - [3.1 `export` / 换行 / `@` 多账号 / `&#` HTML Entity 多格式解析策略](#31-export--换行--多账号--html-entity-多格式解析策略)
   - [3.2 智能解析核心算法逻辑 (Universal Parser Algorithm)](#32-智能解析核心算法逻辑-universal-parser-algorithm)
4. [Android 原生架构与低功耗/高精准定时保证机制](#4-android-原生架构与低功耗高精准定时保证机制)
5. [白虎 vs 青龙 (v2.10 & v2.15+) 全量 API 对齐矩阵](#5-白虎-vs-青龙-v210--v215-全量-api-对齐矩阵)
6. [双面板适配器模式 (IPanelAdapter) 与降级兼容机制](#6-双面板适配器模式-ipaneladapter-与降级兼容机制)
7. [UI 交互设计规范与多级文件夹脚本树结构](#7-ui-交互设计规范与多级文件夹脚本树结构)
8. [变量分段/单字段快捷修改算法 (Sub-Item Editor)](#8-变量分段单字段快捷修改算法-sub-item-editor)
9. [Phase 4: zip.gz 终端恢复包终极方案](#9-phase-4-zipgz-终端恢复包终极方案)

---

## 1. 软件定位与核心原则

1. **绝对个人自用模式 (Personal Use Mode)**：
   - 彻底去除任何强制脱敏遮挡（如 `********`）。
   - 环境变量提供 100% 完整明文原生展示与一键复制功能。
2. **极低资源开销 (Ultra-Low Footprint)**：
   - 客户端 UI 与后台守护进程严格限制 CPU 占用 < 0.5%，内存占用 < 20MB。
   - 禁止在主线程做任何阻塞同步或频繁磁盘写操作。
3. **精准定时保证 (Precision Cron Execution)**：
   - 当在安卓手机上运行本地白虎面板时，本地面板守护进程享有最高优先级，不受 Android 电池优化与后台杀死机制影响，确保定时任务按秒级（如 `0 * * * *`）精准触发。
4. **流行的极简白天/黑夜主题 (Light/Dark Theme)**：
   - 提供优雅的 `Light Theme` (纯白/灰 Slate 简约) 与 `Dark Theme` (深邃暗黑拟态) 一键无缝切换。

---

## 2. 方案隐患剖析与针对性解决策 (Risk Assessment)

### 2.1 安卓 10+ W^X 二进制执行限制与解决策
- **解决方案**：
  将编译好的 `baihu` ARM64 二进制文件在打包 APK 时放置在 `src/main/jniLibs/arm64-v8a/libbaihu.so` 目录。
  安装 APK 时，安卓系统会自动把 `libbaihu.so` 解压释放至系统原生库目录 `/data/app/.../lib/arm64/`，并由系统物理赋予 `r-xp` (可执行) 权限。App 启动时直接调用 `nativeLibraryDir + "/libbaihu.so server"`，完美合规绕过 W^X 拦截！

### 2.2 国内厂商 ROM (MIUI/HarmonyOS/OriginOS) Doze 休眠防挂起策
- 结合 `Foreground Service` 与系统最高的 **`AlarmManager.setAlarmClock()` 物理闹钟级唤醒 Intent**，绝不被 Doze 模式挂起。

### 2.3 Android 9+ Cleartext 局域网 HTTP 访问限制
- 配置 `android:usesCleartextTraffic="true"` 并配置 OkHttp TrustAllManager 支持局域网 IP 及 HTTPS 自签 SSL 证书。

---

## 3. 复杂环境变量多格式智能解析算法与实体解码

在真实使用中，用户复制的环境变量文本格式极度多样，包含：
- **`export` 前缀**：如 `export JD_COOKIE="pt_key=...;pt_pin=...;"`
- **换行分隔 (`\n` / `\r\n`)**：多账号按行排列
- **`@` 符号分隔**：青龙/白虎多账号常用 `@` 拼接（如 `JD_COOKIE="cookie1@cookie2@cookie3"`）
- **HTML 实体编码 (`&#38;`, `&amp;`, `&#39;`)**：网页复制时夹带转义实体
- **URL Percent 编码 (`%20`, `%3D`, `%26`)**：抓包直接复制的未解码 URL 参数

### 3.1 `export` / 换行 / `@` 多账号 / `&#` HTML Entity 多格式解析策略

```mermaid
flowchart TD
    Paste[用户粘贴任意格式文本] --> Clean[HTML Entity 解码: &#38; -> &]
    Clean --> URLDecode[URL Percent 解码: %3D -> =]
    URLDecode --> Regex{正则表达式智能识别}

    Regex -->|包含 export| MatchExport[匹配 export KEY='VAL' 或 export KEY="VAL"]
    Regex -->|包含 @ 分隔| MatchAt[提示或自动拆解多账号: cookie1 @ cookie2]
    Regex -->|多行粘贴 \n| MatchLines[逐行提取 KEY=VALUE]

    MatchExport --> Output[输出结构化环境变量列表]
    MatchAt --> Output
    MatchLines --> Output
```

### 3.2 智能解析核心算法逻辑 (Universal Parser Algorithm)

```javascript
// 通用 HTML Entity & URL 自动解码函数
function sanitizeRawEnvString(rawText) {
  if (!rawText) return '';
  let str = rawText;
  // 1. HTML Entity 字符还原
  str = str.replace(/&#38;/g, '&')
           .replace(/&amp;/g, '&')
           .replace(/&#39;/g, "'")
           .replace(/&quot;/g, '"')
           .replace(/&#61;/g, '=')
           .replace(/&#59;/g, ';');
  
  // 2. URL Percent 编码自动还原 (%3D -> =, %26 -> &)
  try {
    if (str.includes('%3D') || str.includes('%3d') || str.includes('%26')) {
      str = decodeURIComponent(str);
    }
  } catch (e) {
    // 忽略异常，保持原样
  }
  return str;
}

// 智能全格式变量解析算法
function parseUniversalEnvText(inputText) {
  const result = [];
  const text = sanitizeRawEnvString(inputText);

  // 规则 1: 匹配 export KEY="VALUE" 或 export KEY='VALUE' 或 KEY=VALUE
  const exportRegex = /(?:export\s+)?([A-Za-z0-9_]+)\s*=\s*(?:"([^"\r\n]*)"|'([^'\r\n]*)'|([^\r\n]+))/g;
  let match;

  while ((match = exportRegex.exec(text)) !== null) {
    const name = match[1].trim();
    const val = (match[2] || match[3] || match[4] || '').trim();

    if (name) {
      // 规则 2: 如果变量值内部包含 @ 符号 (多账号拼接)，提供拆解或保持原样
      result.push({
        name: name,
        value: val,
        hasAtSplit: val.includes('@')
      });
    }
  }

  // 规则 3: 如果未匹配到 export，按换行符 \n 兜底提取
  if (result.length === 0) {
    const lines = text.split(/\r?\n/);
    lines.forEach((line, idx) => {
      const trimmed = line.trim();
      if (trimmed && trimmed.includes('=')) {
        const eqIdx = trimmed.indexOf('=');
        const k = trimmed.substring(0, eqIdx).replace(/^export\s+/, '').trim();
        const v = trimmed.substring(eqIdx + 1).replace(/^["']|["']$/g, '').trim();
        if (k) result.push({ name: k, value: v, hasAtSplit: v.includes('@') });
      }
    });
  }

  return result;
}
```

---

## 4. Android 原生架构与低功耗/高精准定时保证机制

安卓原生客户端采用 **MVVM + Clean Architecture + Hilt + Retrofit + Jetpack Compose**：

```mermaid
flowchart TD
    App[Android Panel App Application] --> Service[LocalBaihuDaemonService (Foreground Service)]
    Service --> Battery[REQUEST_IGNORE_BATTERY_OPTIMIZATIONS (电池优化白名单)]
    Service --> Lock[Partial WakeLock (保证 CPU 调度休眠时不挂起)]
    Service --> Alarm[AlarmManager.setAlarmClock() 系统闹钟级物理唤醒]
    Service --> SubProcess[Process: :panel_daemon (独立进程运行 libbaihu.so)]

    SubProcess --> Engine[Baihu Go Server (Listen 127.0.0.1:5700)]
    Engine --> Scheduler[Go Cron Scheduler (秒级高精度任务调度)]
```

---

## 5. 白虎 vs 青龙 (v2.10 & v2.15+) 全量 API 对齐矩阵

### 5.1 接口映射对照表

| 业务模块 | 白虎面板 (Baihu Panel) | 青龙 v2.15+ (OpenAPI) | 青龙 v2.10 (Legacy 旧版) | 客户端 Unified 方法 |
| :--- | :--- | :--- | :--- | :--- |
| **基础 Auth** | `POST /api/v1/auth/login`<br>Body: `{username, password}` | `GET /open/auth/token?client_id=...&client_secret=...` | `POST /api/user/login`<br>Body: `{username, password}` | `authenticate(): Result<AuthToken>` |
| **任务列表** | `GET /api/v1/tasks` | `GET /api/crons` | `GET /api/crons` | `getTasks(query): Result<List<UnifiedTask>>` |
| **创建任务** | `POST /api/v1/tasks`<br>Body: `{name, command, schedule, timeout, retry_count}` | `POST /api/crons`<br>Body: `{name, command, schedule, labels}` | `POST /api/crons`<br>Body: `{name, command, schedule}` | `createTask(task): Result<Boolean>` |
| **运行任务** | `POST /api/v1/execute/task/:id` | `PUT /api/crons/run`<br>Body: `["id1", "id2"]` | `PUT /api/crons/run`<br>Query: `id=1` | `runTask(ids): Result<Boolean>` |
| **停止任务** | `POST /api/v1/tasks/stop/:logID` | `PUT /api/crons/stop`<br>Body: `["id1"]` | `PUT /api/crons/stop`<br>Query: `id=1` | `stopTask(ids): Result<Boolean>` |
| **任务开关** | 修改 `enabled: true/false` | `PUT /api/crons/enable`<br>`PUT /api/crons/disable` | `PUT /api/crons/enable`<br>`PUT /api/crons/disable` | `toggleTask(id, enable): Result<Boolean>` |
| **变量列表** | `GET /api/v1/env` | `GET /api/envs` | `GET /api/envs` | `getEnvs(query): Result<List<UnifiedEnv>>` |
| **保存变量** | `POST /api/v1/env`<br>`PUT /api/v1/env/:id` | `POST /api/envs`<br>`PUT /api/envs` | `POST /api/envs`<br>`PUT /api/envs` | `saveEnv(env): Result<Boolean>` |
| **批量保存变量** | `POST /api/v1/env/bulk_save` | `POST /api/envs` (Body Array) | `POST /api/envs` | `bulkSaveEnv(envs): Result<Boolean>` |
| **日志读取** | `GET /api/v1/logs/sse?log_id=...` (SSE 流) | `GET /api/crons/:id/log` | `GET /api/logs/detail?file=...` | `streamTaskLog(logId): Flow<String>` |
| **文件树** | `GET /api/v1/files/tree` | `GET /api/scripts` | `GET /api/scripts` | `getScriptTree(): Result<List<ScriptNode>>` |
| **依赖管理** | `GET /api/v1/deps`<br>`POST /api/v1/deps/install` | `GET /api/dependences`<br>`POST /api/dependences` | `GET /api/dependences` | `getDeps(): Result<List<UnifiedDep>>` |
| **WebShell 终端** | `WS /api/v1/terminal/ws` | WebShell | Terminal API | `connectTerminal(): TerminalSession` |

---

## 6. 双面板适配器模式 (IPanelAdapter) 与降级兼容机制

```kotlin
interface IPanelAdapter {
    val instanceInfo: PanelInstance
    
    suspend fun authenticate(): Result<String>
    suspend fun getTasks(search: String?): Result<List<UnifiedTask>>
    suspend fun runTask(taskIds: List<String>): Result<Boolean>
    suspend fun stopTask(taskIds: List<String>): Result<Boolean>
    suspend fun toggleTask(taskId: String, enable: Boolean): Result<Boolean>
    suspend fun getEnvs(search: String?): Result<List<UnifiedEnv>>
    suspend fun saveEnv(env: UnifiedEnv): Result<Boolean>
    suspend fun deleteEnv(envIds: List<String>): Result<Boolean>
    suspend fun getScriptTree(): Result<List<ScriptNode>>
    suspend fun readScriptContent(path: String): Result<String>
    suspend fun saveScriptContent(path: String, content: String): Result<Boolean>
    fun streamLog(logId: String): Flow<String>
}
```

---

## 7. UI 交互设计规范与多级文件夹脚本树结构

### 7.1 多级文件夹脚本树实现算法
```javascript
// 递归多级文件夹节点渲染算法
function renderFolderLevel(nodes, container) {
  nodes.forEach(node => {
    if (node.isDir) {
      const folderNode = document.createElement('div');
      folderNode.className = 'tree-folder-node';
      
      const folderHeader = document.createElement('div');
      folderHeader.className = 'folder-header';
      folderHeader.innerHTML = `
        <i class="fa-solid ${node.isOpen ? 'fa-folder-open' : 'fa-folder'}"></i>
        <span>${node.name}</span>
      `;

      const folderChildren = document.createElement('div');
      folderChildren.className = 'folder-children';
      folderChildren.style.display = node.isOpen ? 'flex' : 'none';

      folderHeader.addEventListener('click', () => {
        node.isOpen = !node.isOpen;
        renderScriptTree(currentNodes);
      });

      folderNode.appendChild(folderHeader);
      folderNode.appendChild(folderChildren);
      container.appendChild(folderNode);

      if (node.children && node.children.length > 0) {
        renderFolderLevel(node.children, folderChildren);
      }
    } else {
      const fileNode = document.createElement('div');
      fileNode.className = 'tree-file-node';
      fileNode.innerHTML = `<span>${node.name}</span>`;
      fileNode.addEventListener('click', () => openEditor(node));
      container.appendChild(fileNode);
    }
  });
}
```

---

## 8. 变量分段/单字段快捷修改算法 (Sub-Item Editor)

```javascript
// 字符串分段拆解算法
function parseEnvSubItems(rawValue) {
  const pairs = [];
  // 先还原 HTML 实体
  const sanitized = sanitizeRawEnvString(rawValue);
  const parts = sanitized.split(/;|\&/);
  parts.forEach(part => {
    const trimmed = part.trim();
    if (trimmed.includes('=')) {
      const idx = trimmed.indexOf('=');
      const k = trimmed.substring(0, idx).trim();
      const v = trimmed.substring(idx + 1).trim();
      pairs.push({ key: k, value: v });
    }
  });
  return pairs;
}
```

---

## 9. Phase 4: zip.gz 终端恢复包终极方案 (排在最后实施)

当所有核心原生功能与兼容适配完成后，作为 **Phase 4 部署需求** 交付：
- 打包预配置好 Alpine Linux + Baihu Go ARM64 二进制 + Python/Node 环境的 `baihu-termux-mobile-v1.x.zip.gz`。
- 提供 1 行解压恢复指令：
  ```bash
  mkdir -p ~/baihu && tar -xzvf /sdcard/Download/baihu-termux-mobile-v1.x.zip.gz -C ~/baihu && ~/baihu/start.sh
  ```
