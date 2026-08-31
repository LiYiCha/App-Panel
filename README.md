# Panel Hub (App-Panel)

Android 原生跨面板统一管理客户端，面向青龙面板（Qinglong）与白虎面板（Baihu），提供定时任务调度、智能环境变量解析、脚本代码高亮编辑、订阅与仓库同步、依赖版本管理、仪表盘监控及系统维护的一体化移动管理中心。

---

## 🌟 核心特性

- 📱 **跨面板统一体验**：底层深度适配青龙面板（v2.10 ~ v2.18+）与白虎面板（v1.x+），多实例独立存储与秒级无缝切换。
- ⚡ **极速并发拉取**：全并发异步协程驱动，7 大核心接口并行调度，数据刷新由传统 8~10 秒直降至 1~2 秒，配合本地离线持久化秒开即览。
- ⏰ **全功能任务调度**：支持任务实时启停、排队/就绪/运行状态区分、置顶、批量操作、标签过滤、实时 SSE 日志流与执行历史溯源。
- 🔑 **万能环境变量中心**：智能识别标准 JSON、Shell `export`、裸 Cookie、换行多账号、`@`/`&` 拼接并支持一键拆分；内置 HTML 实体反转义与 URL Percent 编解码还原，全量明文展示与一键快捷导出。
- 📝 **代码高亮脚本工作台**：多级脚本文件树浏览、支持 JavaScript / Python / Shell / JSON 语法高亮预览与在线编辑、文件上传与跨目录管理。
- 📦 **依赖与订阅自动化**：Node.js、Python3、Linux 系统依赖版本化安装与日志追踪；支持公开/私有 Git 仓库与单文件订阅同步。
- 📊 **宿主机监控与仪表盘**：CPU / 内存占用、磁盘使用率、Worker 调度状态、每日运行成功率与耗时排行多维统计呈现。
- 🛡️ **高可用与自动自愈网络**：OkHttp 过期连接智能重试与自动建连、全局未捕获异常防护、内置应用级开发者控制台。

---

## 📋 面板支持矩阵

| 面板类型 | 兼容版本 | 适配层实现 | 说明 |
| :--- | :--- | :--- | :--- |
| **青龙面板 (新版)** | v2.15 ~ v2.18+ | `QinglongV15Adapter` | 完整覆盖任务、订阅、多环境依赖、日志树、系统重载与配置维护 |
| **青龙面板 (经典版)** | v2.10 ~ v2.14 | `QinglongV10Adapter` | 覆盖经典版任务调度、环境变量、基础依赖管理与高级配置通道 |
| **白虎面板** | v1.0 及以上 | `BaihuPanelAdapter` | 覆盖 `/api/v1/*` 完整生态：任务、仓库同步、文件管理、宿主机指标监控与审计 |

---

## 🚀 功能全景

### 1. 任务调度中心 (Tasks)
- **多状态感知**：精准感知「已启用」、「已禁用」、「排队中」、「运行中」多重状态，支持彩色状态徽章展示。
- **快捷控制**：单项快速启停、即时触发运行、置顶排列、批量启用/禁用/删除。
- **实时日志与历史**：基于 SSE / Flow 的流式任务运行日志实时输出；精确关联执行实例的退出状态、运行时长与历史记录。

### 2. 智能环境变量中心 (Envs)
- **通用解析器 `UniversalEnvParser`**：
  - 支持直接粘贴多行 Shell 语法（`export KEY="VALUE"`）；
  - 支持标准 JSON 数组导入与导出；
  - 智能拆分由换行、`&` 或 `@` 分隔的多账号 Cookie 文本；
  - 自动还原 HTML 实体（`&#38;`、`&amp;` 等）与 URL Percent 编码（`%3D`、`%26` 等）。
- **便捷维护**：全量明文展示无遮挡、快速编辑修改、一键单键复制、导出为标准脚本格式。

### 3. 脚本与配置工作台 (Scripts & Configs)
- **代码高亮编辑器**：内置基于 Compose Canvas 的高性能语法着色器，支持 JavaScript、Python、Shell、JSON 关键词、字符串、数字、注释及函数名高亮显示。
- **文件树管理**：支持多级文件夹下钻展开、文件滑动删除、新建脚本、本地文件上传及文件重命名。
- **配置文件在线修改**：针对青龙 `config.sh`、`extra.sh` 与白虎 `config.json` 等关键配置文件提供免终端直修能力。

### 4. 依赖环境管理 (Dependencies)
- 分类收纳 `nodejs`、`python3`、`linux` 三大运行环境依赖；
- 支持在线搜索、指定依赖版本安装、重新安装、卸载与强制清理；
- 实时追踪依赖安装日志与状态变更。

### 5. 订阅与仓库同步 (Subscriptions)
- 适配公共 GitHub / Gitee 仓库、私有 Token 鉴权仓库与单脚本订阅来源；
- 支持分支指定、白名单/黑名单正则过滤、依赖文件识别与脚本后缀筛选；
- 支持定时自动拉取同步并下发调度任务。

### 6. 系统仪表盘与监控 (Dashboard & Settings)
- **性能指标看板**：实时读取宿主机 CPU 占用、物理内存水位、系统平台与调度 Worker 状态。
- **本地缓存加速**：首次进入直接调取本地持久化快照，网络返回后平滑刷新。
- **高级系统配置**：
  - 青龙面板：支持任务并发数、日志保留天数在线设定、系统配置热重载与通知连通性测试；
  - 白虎面板：支持宿主机状态概览与服务环境监测；
  - 内置开发者控制台：全链路 HTTP 流量审查与未捕获异常监控。

---

## 🛠️ 技术架构

- **开发语言**：Kotlin 2.1.0（100% 纯 Kotlin 编写）
- **界面体系**：Jetpack Compose + Material Design 3（动态主题支持，适配深色模式）
- **架构模式**：MVVM + Repository 模式 + Adapter 适配器模式（隔离异构面板差异）
- **网络通信**：Retrofit 2.11.0 + OkHttp 4.12.0（自签名 SSL 信任、内存 CookieJar 维系会话、过期连接自愈重试）
- **本地持久化**：Room 2.6.1 + SharedPreferences 本地快照
- **依赖注入**：Dagger Hilt 2.52 + KSP 2.1.0-1.0.29
- **并发与响应式**：Kotlin Coroutines 1.9.0（`async` 并发网络加载）+ Flow 响应式数据流
- **编译指标**：`minSdk 26` (Android 8.0)，`compileSdk 36`，`targetSdk 36`，目标架构 `arm64-v8a`

---

## 📦 项目结构

```
App-Panel/
├── .github/
│   └── workflows/
│       └── release.yml          # GitHub Actions 自动化发布工作流
├── panel-app/                   # Android 工程主目录
│   ├── app/
│   │   ├── src/main/java/com/panel/app/
│   │   │   ├── data/
│   │   │   │   ├── adapter/     # 青龙与白虎面板适配器层
│   │   │   │   ├── local/       # Room 数据库实体与 DAO
│   │   │   │   ├── logger/      # 内置开发者控制台与异常监控日志
│   │   │   │   ├── model/       # 跨面板统一数据模型
│   │   │   │   ├── parser/      # 环境变量万能解析器
│   │   │   │   ├── remote/      # 网络客户端与 Retrofit 接口
│   │   │   │   └── repository/  # 面板数据仓库与本地缓存
│   │   │   └── ui/
│   │   │       ├── components/  # 代码高亮编辑器与通用组件
│   │   │       ├── screens/     # 各大业务页面
│   │   │       ├── theme/       # Material3 主题与配色
│   │   │       └── viewmodel/   # 核心状态调度 ViewModel
│   │   └── build.gradle.kts
│   └── gradle/libs.versions.toml # 依赖版本与构建参数统一管理
├── release/                     # 签名发布 APK 归档目录
├── release.ps1                  # 本地一键打包签名与发布脚本
└── README.md
```

---

## 🔨 本地构建与发布

### 1. 调试编译
```bash
cd panel-app
./gradlew assembleDebug
```
产物 APK 生成于 `panel-app/app/build/outputs/apk/debug/app-debug.apk`。

### 2. 正式签名打包
正式版需要使用签名证书：
```bash
cd panel-app
./gradlew assembleRelease
```
产物 APK 生成于 `panel-app/app/build/outputs/apk/release/app-release.apk`。

### 3. 一键自动化发版脚本 (Windows PowerShell)
```powershell
.\release.ps1 -Version 2.0.0 -Message "Panel Hub 2.0.0 正式版发布"
```
脚本将自动完成：
1. 更新版本元数据；
2. 执行 Release 签名混淆构建；
3. 导出命名规范的 APK 至 `release/` 目录；
4. 创建 Git 标签并推送到远程 GitHub 仓库，触发 GitHub Actions 持续集成。

---

## 📄 开源许可

本项目依据根目录下 [LICENSE](LICENSE) 许可协议开源分发。
