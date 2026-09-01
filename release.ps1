param(
    [Parameter(Mandatory = $true)]
    [string]$Version,

    [Parameter(Mandatory = $false)]
    [string]$Message = "Release version $Version"
)

$ErrorActionPreference = "Stop"

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "Kai Shi Zhi Xing Panel Hub Yi Jian Da Bao Qian Ming Yu Fa Ban Liu Cheng" -ForegroundColor Cyan
Write-Host "Mubiao Ban Ben: $Version" -ForegroundColor Yellow
Write-Host "Ban Ben Shuo Ming: $Message" -ForegroundColor Yellow
Write-Host "==========================================" -ForegroundColor Cyan

# 1. Normalize tag name
$tag = if ($Version.StartsWith("v")) { $Version } else { "v$Version" }
$rawVersion = $tag.TrimStart("v")

# 2. Update version config
$tomlPath = Join-Path $PSScriptRoot "panel-app\gradle\libs.versions.toml"
if (Test-Path $tomlPath) {
    Write-Host "[1/5] Updating libs.versions.toml versionName to $rawVersion..." -ForegroundColor Green
    $content = Get-Content -Path $tomlPath -Raw -Encoding UTF8
    $replacement = 'versionName = "' + $rawVersion + '"'
    $newContent = [regex]::Replace($content, 'versionName\s*=\s*"[^"]*"', $replacement)
    [System.IO.File]::WriteAllText($tomlPath, $newContent, (New-Object System.Text.UTF8Encoding $false))
}

# 3. Build signed Release APK
Write-Host "[2/5] Building signed Release APK (assembleRelease)..." -ForegroundColor Green
$panelAppDir = Join-Path $PSScriptRoot "panel-app"
Push-Location $panelAppDir
try {
    .\gradlew.bat assembleRelease --no-daemon
} finally {
    Pop-Location
}

# 4. Copy APK to release directory
$outputApk = Join-Path $PSScriptRoot "panel-app\app\build\outputs\apk\release\app-release.apk"
$releaseDir = Join-Path $PSScriptRoot "release"
if (-not (Test-Path $releaseDir)) {
    New-Item -ItemType Directory -Path $releaseDir | Out-Null
}
$targetApk = Join-Path $releaseDir "Panel-App-$tag.apk"

if (Test-Path $outputApk) {
    Copy-Item -Path $outputApk -Destination $targetApk -Force
    Write-Host "[3/5] Signed APK exported: $targetApk" -ForegroundColor Green
} else {
    Write-Warning "Release APK not found. Check build.gradle.kts config."
}

# 5. Git commit, tag and push
Write-Host "[4/5] Committing git changes and tagging $tag..." -ForegroundColor Green
if (-not (Test-Path (Join-Path $PSScriptRoot ".git"))) {
    git init
    git branch -M main
    git remote add origin https://github.com/LiYiCha/App-Panel.git
}

git add .
git commit -m "chore(release): $tag - $Message"
git tag -a $tag -m "$Message" -f

Write-Host "[5/5] Pushing to remote GitHub repository..." -ForegroundColor Green
git push origin main
git push origin $tag --force

# 6. Upload APK to GitHub Release if GITHUB_TOKEN is available
if (-not $env:GITHUB_TOKEN) {
    $localPropsPath = Join-Path $panelAppDir "local.properties"
    if (Test-Path $localPropsPath) {
        $tokenLine = Get-Content -Path $localPropsPath | Where-Object { $_ -match '^GITHUB_TOKEN=' }
        if ($tokenLine) {
            $env:GITHUB_TOKEN = $tokenLine.Substring('GITHUB_TOKEN='.Length)
        }
    }
}

if ($env:GITHUB_TOKEN) {
    Write-Host "GITHUB_TOKEN detected, uploading APK to GitHub Release..." -ForegroundColor Green
    try {
        $headers = @{
            "Authorization" = "token $env:GITHUB_TOKEN"
            "Accept" = "application/vnd.github.v3+json"
        }
        $relJson = ConvertTo-Json -InputObject @{
            tag_name = $tag
            name = "Panel Hub $tag Release"
            body = $Message
            draft = $false
            prerelease = $false
        }
        $createReleaseUrl = "https://api.github.com/repos/LiYiCha/App-Panel/releases"
        $relResp = Invoke-RestMethod -Uri $createReleaseUrl -Method Post -Headers $headers -Body $relJson -ContentType "application/json"
        
        $uploadUrl = $relResp.upload_url.Replace("{?name,label}", "?name=Panel-App-$tag.apk")
        $apkBytes = [System.IO.File]::ReadAllBytes($targetApk)
        $uploadHeaders = @{
            "Authorization" = "token $env:GITHUB_TOKEN"
            "Content-Type" = "application/vnd.android.package-archive"
        }
        Invoke-RestMethod -Uri $uploadUrl -Method Post -Headers $uploadHeaders -Body $apkBytes | Out-Null
        Write-Host "Release and APK asset published successfully!" -ForegroundColor Green
    } catch {
        Write-Warning "Failed to upload Release: $($_.Exception.Message)"
    }
}

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "Build and publish complete!" -ForegroundColor Green
Write-Host "APK path: $targetApk" -ForegroundColor Green
Write-Host "GitHub repo: https://github.com/LiYiCha/App-Panel" -ForegroundColor Green
Write-Host "Web release link: https://github.com/LiYiCha/App-Panel/releases/new?tag=$tag" -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan
