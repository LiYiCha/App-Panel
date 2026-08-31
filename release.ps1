param(
    [Parameter(Mandatory = $true)]
    [string]$Version,

    [Parameter(Mandatory = $false)]
    [string]$Message = "Release version $Version"
)

$ErrorActionPreference = "Stop"

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "开始执行 Panel Hub 一键打包签名与发版流程" -ForegroundColor Cyan
Write-Host "目标版本: $Version" -ForegroundColor Yellow
Write-Host "版本说明: $Message" -ForegroundColor Yellow
Write-Host "==========================================" -ForegroundColor Cyan

# 1. 规范化 Tag 名称
$tag = if ($Version.StartsWith("v")) { $Version } else { "v$Version" }
$rawVersion = $tag.TrimStart("v")

# 2. 更新版本配置
$tomlPath = Join-Path $PSScriptRoot "panel-app\gradle\libs.versions.toml"
if (Test-Path $tomlPath) {
    Write-Host "[1/5] 正在更新 libs.versions.toml versionName 为 $rawVersion..." -ForegroundColor Green
    $content = Get-Content -Path $tomlPath -Raw -Encoding UTF8
    $replacement = 'versionName = "' + $rawVersion + '"'
    $newContent = [regex]::Replace($content, 'versionName\s*=\s*"[^"]*"', $replacement)
    Set-Content -Path $tomlPath -Value $newContent -Encoding UTF8
}

# 3. 编译打包已签名的 Release APK
Write-Host "[2/5] 正在使用 ycKey 证书执行 Gradle 签名打包 (assembleRelease)..." -ForegroundColor Green
$panelAppDir = Join-Path $PSScriptRoot "panel-app"
Push-Location $panelAppDir
try {
    .\gradlew.bat assembleRelease --no-daemon
} finally {
    Pop-Location
}

# 4. 提取生成的已签名 APK
$outputApk = Join-Path $PSScriptRoot "panel-app\app\build\outputs\apk\release\app-release.apk"
$releaseDir = Join-Path $PSScriptRoot "release"
if (-not (Test-Path $releaseDir)) {
    New-Item -ItemType Directory -Path $releaseDir | Out-Null
}
$targetApk = Join-Path $releaseDir "Panel-App-$tag.apk"

if (Test-Path $outputApk) {
    Copy-Item -Path $outputApk -Destination $targetApk -Force
    Write-Host "[3/5] 已签名 APK 导出成功: $targetApk" -ForegroundColor Green
} else {
    Write-Warning "未找到 release APK，请检查 build.gradle.kts 配置。"
}

# 5. Git 提交、打 Tag 并推送到 GitHub 远程仓库
Write-Host "[4/5] 正在提交 Git 变更并打发布标签 $tag..." -ForegroundColor Green
if (-not (Test-Path (Join-Path $PSScriptRoot ".git"))) {
    git init
    git branch -M main
    git remote add origin https://github.com/LiYiCha/App-Panel.git
}

git add .
git commit -m "chore(release): $tag - $Message"
git tag -a $tag -m "$Message" -f

Write-Host "[5/5] 正在推送到远程 GitHub 仓库..." -ForegroundColor Green
git push origin main
git push origin $tag --force

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "一键打包与发布完成！" -ForegroundColor Green
Write-Host "签名 APK 路径: $targetApk" -ForegroundColor Green
Write-Host "GitHub 仓库: https://github.com/LiYiCha/App-Panel" -ForegroundColor Green
Write-Host "==========================================" -ForegroundColor Cyan
