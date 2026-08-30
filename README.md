# Panel Hub (App-Panel)

现代化跨面板（青龙面板 + 白虎面板）Android 原生移动管理客户端。

## 🌟 核心功能

- **定时任务 & 仓库同步一体化**：支持普通调度任务与 Git 仓库同步任务（分支、白名单、自动解析定时规则）无缝切换与管理。
- **全格式环境变量解析**：智能识别标准 JSON、Bare Cookie、Shell Export、多账号 `@` / `&` 拆分与原子字段修改。
- **多面板与账号隔离管理**：青龙（v2.10 - v2.20.2）与白虎面板多实例秒级切换，状态彻底隔离无污染。
- **任务执行历史与日志中心**：全平台执行流水归档、状态与耗时检索、底层服务端日志目录树下钻浏览。
- **依赖环境与包管理**：Node.js、Python 等依赖一键批量安装、卸载与重试。
- **在线 OTA 自动更新**：集成 GitHub Releases 在线检测与更新，支持自动化 GitHub Actions 构建发版。

## 🚀 自动发布流程 (GitHub Actions)

项目已配置 `.github/workflows/release.yml`。每次发布新版本时，只需在本地或 GitHub 打上 Tag 并推送：

```bash
git tag v1.0.1
git push origin v1.0.1
```

GitHub Actions 将在云端自动编译出 `Panel-App-v1.0.1.apk` 并自动创建 Release，客户端即可直接在线检测并下载安装！
