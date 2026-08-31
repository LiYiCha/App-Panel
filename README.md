# App-Panel（Panel Hub）

Android 原生跨面板管理客户端，面向青龙面板与白虎面板，提供任务调度、环境变量、脚本与配置、依赖管理及运行监控的一体化移动管理能力。

---

## 一、项目概述

本项目以 Kotlin 与 Jetpack Compose 编写，采用 MVVM 分层结构，并通过适配器模式对异构面板接口进行统一抽象。

- 应用包名：`com.panel.app`
- 当前版本：`1.4.0`（以 `panel-app/app/build.gradle.kts` 中的 `versionName` 为准）
- 构建环境：JDK 17，Gradle 8.14.3，Android Gradle Plugin 8.13.0，Kotlin 2.1.0
- 编译与目标 SDK：`compileSdk 36`、`targetSdk 36`，`minSdk 26`（Android 8.0）
- 支持架构：仅 `arm64-v8a`

客户端不涉及任何服务端实现，全部能力均通过面板既有 HTTP 接口完成对接。

---

## 二、面板支持范围

| 面板类型 | 适配实现 | 说明 |
| :--- | :--- | :--- |
| 白虎面板 | `BaihuPanelAdapter` | 采用 `/api/v1/*` 路由体系，覆盖任务、环境变量、文件、依赖、监控与审计模块 |
| 青龙面板 v2.15 及以上 | `QinglongV15Adapter` | 运行时读取 `GET /api/system/config` 获取版本号并缓存，据此在不同版本接口间精确选路 |
| 青龙面板 v2.10（旧版） | `QinglongV10Adapter` | 使用旧版鉴权与接口；青龙 v2.10 无独立订阅模块，相应功能不可用 |

三套适配器统一实现 `IPanelAdapter` 接口，由 `PanelAdapterFactory` 依据面板类型分发，业务层无需区分底层面板差异。

---

## 三、功能说明

### 3.1 任务调度

- 任务列表检索，支持运行、停止、启用、禁用、置顶、取消置顶与批量删除。
- 任务详情维护，含命令、定时规则、标签、超时时间等字段。
- 任务状态实时区分：就绪、队列中、运行中。

### 3.2 订阅与仓库同步

- 支持公开仓库、私有仓库与单文件三类来源。
- 可配置分支、白名单、黑名单、依赖文件、脚本后缀与唯一别名。
- 支持自动添加与自动失效删除定时任务。
- 在白虎面板下对应「仓库同步」语义，界面按面板类型自适应呈现。

### 3.3 执行历史与日志

- 归档任务历次执行实例，记录起止时间、耗时、退出码与执行状态。
- 依据服务端返回的日志路径精确读取当次执行日志，避免日志错配。
- 通过 `Flow` 以 SSE 方式流式输出实时任务日志。

### 3.4 环境变量

- 全量明文展示，不做任何脱敏遮挡，支持一键复制。
- 通用解析器 `UniversalEnvParser` 支持以下输入格式：
  - 标准 JSON 数组导出格式；
  - `export KEY="VALUE"` 与 `export KEY='VALUE'` Shell 写法；
  - 裸 Cookie 文本；
  - 换行分隔的多账号文本；
  - 以 `@`、`&` 拼接的多账号文本，可按需拆分；
  - HTML 实体转义（`&#38;`、`&amp;`、`&#39;`、`&#34;`、`&#61;`、`&#59;`）与 URL Percent 编码（`%3D`、`%26`）自动还原。
- 支持单条新增、编辑、启停与批量删除，并可将全部变量导出为 Shell `export` 或 JSON 数组格式。

### 3.5 脚本与配置文件

- 多级目录文件树浏览，支持新建、编辑、保存、删除脚本与目录。
- 支持从设备文件系统上传本地脚本至面板。
- 配置文件在线编辑，涵盖青龙 `config.sh`、`extra.sh` 与白虎 `config.json` 等。

### 3.6 依赖管理

- 按 `nodejs`、`python3`、`linux` 三类分别管理。
- 支持指定版本安装、卸载、批量删除与强制删除。
- 展示安装状态（安装中、已安装、安装失败、卸载中）并可调取安装日志。

### 3.7 监控、审计与日志中心

- 系统监控：采集服务端 CPU 与内存占用，在设置页以指标卡片呈现。
- 登录审计：记录鉴权流水，含来源 IP、客户端标识、结果与时间。
- 服务端日志：以目录树下钻方式浏览服务端日志文件并查看正文。
- 开发者模式：开启后可进入开发者控制台进行接口排错。

### 3.8 多面板实例与本地设置

- 面板实例通过 Room 持久化存储，支持多实例登记与秒级切换，各实例状态相互隔离。
- 支持请求超时时间配置、明暗主题切换、面板运行状态指标显示。
- 全部构建参数（命名空间、SDK 版本、版本号、JVM 目标）集中于 `gradle/libs.versions.toml` 的 `[versions]` 段统一维护。

### 3.9 本地守护引擎

- 由 `LocalBaihuDaemonService` 于独立进程 `:panel_daemon` 中运行本地白虎面板引擎。
- 以前台服务结合 `PARTIAL_WAKE_LOCK` 维持运行，并监听开机广播实现自启动。
- 自 `nativeLibraryDir` 执行 `libbaihu.so`，以规避 Android 10 及以上版本的 W^X 二进制执行限制。
- 引擎默认监听 `127.0.0.1:5700`。

### 3.10 在线更新

- 通过 GitHub Releases 检测最新版本，按语义化版本比较判定是否需要更新。
- 解析 Release 资源中的 APK 下载地址，供客户端获取安装包。
- 版本来源固定为仓库 `LiYiCha/App-Panel`。

---

## 四、技术栈

| 领域 | 选型 |
| :--- | :--- |
| 界面 | Jetpack Compose（Material3）、Navigation Compose |
| 架构 | MVVM、适配器模式、Repository 层 |
| 网络 | Retrofit 2.11.0、OkHttp 4.12.0、Gson 转换器 |
| 本地存储 | Room 2.6.1 |
| 依赖注入 | Hilt 2.52（KSP 注解处理） |
| 异步 | Kotlin Coroutines 1.9.0 |
| 编译目标 | Java 17 |

网络层允许明文传输（`usesCleartextTraffic="true"`），以适配局域网 HTTP 访问与自签名证书场景。

---

## 五、目录结构

```
App-Panel/
├── .github/
│   └── workflows/
│       └── release.yml          # Tag 触发的构建与发布工作流
├── panel-app/                   # Android 工程根目录
│   │   ├── build.gradle.kts
│   │   ├── proguard-rules.pro     # R8 混淆与保留规则
│   │   └── src/main/java/com/panel/app/
│   │       ├── data/
│   │       │   ├── adapter/     # 三套面板适配器与统一接口
│   │       │   ├── local/       # Room 数据库与本地守护服务
│   │       │   ├── model/       # 统一数据模型
│   │       │   ├── parser/      # 环境变量通用解析器
│   │       │   ├── remote/      # Retrofit 接口与网络客户端
│   │       │   └── repository/
│   │       ├── ui/
│   │       │   ├── screens/     # 各功能页面
│   │       │   ├── theme/
│   │       │   └── viewmodel/
│   │       └── util/            # 在线更新管理
│   └── docs/                    # 设计与接口参考文档
├── PANEL_FEATURE_MATRIX.md      # 面板 API 全景比对与修复记录
├── release.ps1                  # 本地一键打包发版脚本
└── README.md
```

---

## 六、构建与发布

### 6.1 本地构建

```bash
cd panel-app
./gradlew assembleRelease
```

Windows 环境可使用 `gradlew.bat`。产物位于 `panel-app/app/build/outputs/apk/release/`。

### 6.2 一键发版脚本

```powershell
.\release.ps1 -Version 1.4.0 -Message "发布说明"
```

脚本依次执行：更新 `versionName`、签名打包、导出 APK 至 `release/Panel-App-v1.4.0.apk`、提交变更、创建附注标签并推送至远程仓库。

### 6.3 持续集成

`.github/workflows/release.yml` 在推送形如 `v*` 的标签时触发，于 `ubuntu-latest` 环境下使用 JDK 17 执行 `assembleRelease`，并通过 `softprops/action-gh-release` 自动创建 Release 并上传 APK。

### 6.4 签名说明

发布构建类型依赖位于 `panel-app/key/ycKey.jks` 的签名证书。该证书已被 `.gitignore` 排除，未随仓库分发，故：

- 本地执行签名构建前须自备证书，并使其路径与别名同 `build.gradle.kts` 中的配置一致；
- 上述工作流在缺少签名材料时执行 `assembleRelease` 将构建失败，需在持续集成环境中通过仓库机密注入签名配置，或改用无签名构建变体。

---

## 七、相关文档

- `panel-app/docs/DESIGN.md`：架构设计、接口对齐矩阵与关键算法说明。
- `panel-app/docs/PANEL_API_REFERENCE.md`：面板接口参考。
- `PANEL_FEATURE_MATRIX.md`：青龙与白虎面板真实接口比对及客户端修复记录。

---

## 八、许可

本项目遵循仓库根目录 `LICENSE` 所列许可条款。
