package com.panel.app.ui.screens

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AppRegistration
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** 单个权限的实时状态 */
private data class PermissionState(
    val notificationEnabled: Boolean = true,
    val canInstallPackages: Boolean = true,
    val storageHandled: Boolean = true
)

/**
 * 应用权限管理二级页面：分组卡片 + 彩色图标 tile + 状态色徽章。
 * 权限按「通知 / 安装 / 存储」分组，点击卡片直达对应系统授权页；
 * 从系统设置返回时自动刷新状态。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPermissionsScreen(
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    val context = LocalContext.current

    // 实时状态：进入页面与从系统设置返回时刷新
    var perms by remember { mutableStateOf(readPermissionState(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                perms = readPermissionState(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val grantedCount = listOf(perms.notificationEnabled, perms.canInstallPackages, perms.storageHandled).count { it }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                title = {
                    Column {
                        Text("应用权限", fontSize = 15.sp, style = MaterialTheme.typography.titleMedium)
                        Text("$grantedCount / 3 项已就绪", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", modifier = Modifier.size(18.dp))
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // ===== 授权概览 =====
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (grantedCount == 3) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.errorContainer
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "$grantedCount",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (grantedCount == 3) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (grantedCount == 3) "全部权限已就绪" else "有 ${3 - grantedCount} 项权限需要授权",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "点击对应权限卡片可跳转系统设置授权",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // ===== 核心权限 =====
            SectionLabel("核心权限")
            PermissionCard(
                icon = Icons.Default.Notifications,
                tileColor = if (perms.notificationEnabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                tileTint = if (perms.notificationEnabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                title = "通知权限",
                desc = "任务执行通知、更新提醒推送",
                granted = perms.notificationEnabled,
                onClick = { openNotificationSettings(context) }
            )
            PermissionCard(
                icon = Icons.Default.AppRegistration,
                tileColor = if (perms.canInstallPackages) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                tileTint = if (perms.canInstallPackages) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                title = "安装未知应用",
                desc = "从应用内下载并安装更新包",
                granted = perms.canInstallPackages,
                onClick = { openInstallSettings(context) }
            )

            // ===== 存储 =====
            SectionLabel("存储")
            PermissionCard(
                icon = Icons.Default.Storage,
                tileColor = MaterialTheme.colorScheme.primaryContainer,
                tileTint = MaterialTheme.colorScheme.onPrimaryContainer,
                title = "脚本与备份存储",
                desc = "使用应用专属目录（Android/media），无需手动授权",
                granted = true,
                grantedLabel = "无需授权",
                onClick = { openAppSettings(context) }
            )

            // ===== 更多 =====
            SectionLabel("更多")
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable {
                        runCatching {
                            context.startActivity(
                                Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                            )
                        }
                    },
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("系统应用信息", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text("查看全部权限与高级设置", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                "从系统设置返回本页时，权限状态自动刷新。",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
    }
}

/** 读取全部权限状态 */
private fun readPermissionState(context: android.content.Context): PermissionState {
    val notificationEnabled = runCatching {
        ContextCompat.getSystemService(context, android.app.NotificationManager::class.java)
            ?.areNotificationsEnabled() ?: true
    }.getOrElse { true }

    // 安装未知应用（Android 8.0+，canRequestPackageInstalls 为隐藏 API，反射调用）
    val canInstall = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        runCatching {
            val method = android.provider.Settings::class.java
                .getDeclaredMethod("canRequestPackageInstalls", android.content.Context::class.java)
            method.isAccessible = true
            method.invoke(null, context) as Boolean
        }.getOrElse { true }
    } else true

    // 旧版存储权限已声明才需要检查；分区存储无需授权
    val storageHandled = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        val declared = info.requestedPermissions?.toList() ?: emptyList()
        val needsLegacy = declared.any {
            it == android.Manifest.permission.READ_EXTERNAL_STORAGE || it == android.Manifest.permission.WRITE_EXTERNAL_STORAGE
        }
        !needsLegacy || ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
    }.getOrElse { true }

    return PermissionState(notificationEnabled, canInstall, storageHandled)
}

private fun openNotificationSettings(context: android.content.Context) {
    runCatching {
        context.startActivity(
            Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }.onFailure {
        openAppSettings(context)
    }
}

private fun openInstallSettings(context: android.content.Context) {
    runCatching {
        context.startActivity(
            Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }.onFailure {
        runCatching {
            context.startActivity(
                Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }
    }
}

private fun openAppSettings(context: android.content.Context) {
    runCatching {
        context.startActivity(
            Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, fontSize = 11.sp, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
}

/** 权限卡片：彩色图标 tile + 标题/描述 + 状态徽章，整卡可点去授权 */
@Composable
private fun PermissionCard(
    icon: ImageVector,
    tileColor: Color,
    tileTint: Color,
    title: String,
    desc: String,
    granted: Boolean,
    grantedLabel: String = "已授权",
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(tileColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = tileTint)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(desc, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val (label, color) = if (granted) grantedLabel to MaterialTheme.colorScheme.primary
            else "未授权" to MaterialTheme.colorScheme.error
            Text(
                label,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = color,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(color.copy(alpha = 0.12f))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
    }
}
