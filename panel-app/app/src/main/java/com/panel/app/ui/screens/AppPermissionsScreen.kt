package com.panel.app.ui.screens

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.core.content.ContextCompat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 应用权限管理二级页面。
 * 展示当前 App 需要的各项权限状态，支持一键跳转系统设置授权。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPermissionsScreen(
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    val context = LocalContext.current

    // 检查安装未知应用权限（Android 8.0+）
    // 注意：canRequestPackageInstalls 是隐藏 API，通过反射调用
    val canInstallPackages = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching {
                val method = android.provider.Settings::class.java
                    .getDeclaredMethod("canRequestPackageInstalls", android.content.Context::class.java)
                method.isAccessible = true
                method.invoke(null, context) as Boolean
            }.getOrElse { true } // 反射失败则默认认为已授权
        } else {
            true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("应用权限管理", fontSize = 15.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 标题说明
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        "权限说明",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "Panel Hub 需要以下权限来提供完整功能。部分权限需要在系统设置中手动授权。",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 安装未知应用权限
            PermissionItemCard(
                icon = Icons.Default.AppRegistration,
                title = "安装未知应用",
                description = "允许从浏览器或文件管理器安装 APK 文件（用于应用更新）",
                status = if (canInstallPackages) "已授权" else "未授权",
                statusColor = if (canInstallPackages) Color(0xFF10B981) else MaterialTheme.colorScheme.error,
                onAction = {
                    try {
                        context.startActivity(
                            Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                                data = Uri.parse("package:${context.packageName}")
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                        )
                    } catch (_: Exception) {
                        try {
                            context.startActivity(
                                Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                            )
                        } catch (_: Exception) {}
                    }
                },
                actionLabel = "去授权"
            )
            // 取消授权说明
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f))
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        "取消授权",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        "如需取消本应用的安装权限，请前往：系统设置 → 应用 → Panel Hub → 安装未知应用 → 关闭权限",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = {
                            try {
                                context.startActivity(
                                    Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                )
                            } catch (_: Exception) {}
                        },
                        modifier = Modifier.align(Alignment.End),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("前往取消授权", fontSize = 11.sp)
                    }
                }
            }

            // 存储权限（Android 10+ 分区存储，本项目无需声明 READ_EXTERNAL_STORAGE）
            // 日志/脚本/备份均使用 Android/media/<包名>/ 私有目录，天然免授权
            val hasStoragePermission = remember {
                // 检查 Manifest 是否声明了旧版存储权限（向下兼容保留判断逻辑）
                try {
                    val packageInfo = context.packageManager.getPackageInfo(
                        context.packageName, PackageManager.GET_PERMISSIONS
                    )
                    val declared = packageInfo.requestedPermissions?.toList() ?: emptyList()
                    val hasOldStoragePerm = declared.any { perm ->
                        perm == android.Manifest.permission.READ_EXTERNAL_STORAGE ||
                                perm == android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                    }
                    if (!hasOldStoragePerm) return@remember true // 未声明旧版权限 = 使用分区存储，无需授权
                    ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                } catch (_: Exception) {
                    true
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                PermissionItemCard(
                    icon = Icons.Default.Storage,
                    title = "读取存储",
                    description = if (!hasStoragePermission)
                        "项目使用分区存储，无需申请此权限即可访问应用专属媒体目录"
                    else
                        "读取和写入外部存储，用于保存脚本、日志和备份文件",
                    status = if (hasStoragePermission) "无需授权 ✓" else "未授权",
                    statusColor = if (hasStoragePermission) Color(0xFF10B981) else MaterialTheme.colorScheme.error,
                    onAction = {
                        try {
                            context.startActivity(
                                Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                            )
                        } catch (_: Exception) {
                            try {
                                context.startActivity(
                                    Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                )
                            } catch (_: Exception) {}
                        }
                    },
                    actionLabel = if (hasStoragePermission) "" else "去授权"
                )
            }

            // 通知权限
            val notificationEnabled = remember {
                runCatching {
                    val manager = ContextCompat.getSystemService(context, android.app.NotificationManager::class.java)
                    manager?.areNotificationsEnabled() ?: true
                }.getOrElse { true }
            }

            PermissionItemCard(
                icon = Icons.Default.Notifications,
                title = "通知权限",
                description = "接收任务执行通知、更新提醒等重要消息推送",
                status = if (notificationEnabled) "已授权" else "未授权",
                statusColor = if (notificationEnabled) Color(0xFF10B981) else MaterialTheme.colorScheme.error,
                onAction = {
                    try {
                        context.startActivity(
                            Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                        )
                    } catch (_: Exception) {
                        try {
                            context.startActivity(
                                Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                            )
                        } catch (_: Exception) {}
                    }
                },
                actionLabel = "去授权"
            )

            // 系统设置入口
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        try {
                            context.startActivity(
                                Intent(android.provider.Settings.ACTION_APPLICATION_SETTINGS).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                            )
                        } catch (_: Exception) {}
                    },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier
                        .padding(14.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text("打开系统设置", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Text("查看所有应用权限管理选项", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(
                        Icons.Default.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PermissionItemCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    status: String,
    statusColor: Color,
    onAction: () -> Unit,
    actionLabel: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text(description, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(status, fontSize = 11.sp, color = statusColor)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = onAction,
                    enabled = status != "已授权"
                ) {
                    Text(actionLabel, fontSize = 11.sp)
                }
            }
        }
    }
}
