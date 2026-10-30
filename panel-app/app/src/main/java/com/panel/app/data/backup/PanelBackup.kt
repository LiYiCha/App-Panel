package com.panel.app.data.backup

import java.util.Date

/**
 * 面板备份 / 恢复数据模型。
 *
 * 设计目标：
 * - 一个统一 JSON 文件即可在不同手机、不同面板（青龙/白虎）间迁移
 * - 任务 / 环境变量 / 脚本 字段在两侧面板之间对齐，可直接跨类型恢复
 * - 配置文件因面板结构不同，只在同类型面板间恢复
 */
data class PanelBackup(
    val schemaVersion: Int = 2,
    val exportedAt: String,
    val sourcePanelType: String? = null,
    val sourcePanelName: String? = null,
    val tasks: List<BackupTask> = emptyList(),
    val envs: List<BackupEnv> = emptyList(),
    val scripts: List<BackupScript> = emptyList(),
    val configFiles: List<BackupConfigFile> = emptyList(),
    // v2 新增：本地数据
    val localScripts: List<BackupLocalScript> = emptyList(),
    val panels: List<BackupPanelInstance> = emptyList(),
    // v2 新增：环境变量分段规则（env_seg_prefs 全量条目原样导出，"seg_<变量名>" -> JSON 字符串）
    val envSegRules: Map<String, String> = emptyMap()
)

data class BackupTask(
    val name: String,
    val command: String,
    val schedule: String,
    val labels: List<String>? = null,
    val isDisabled: Boolean = false,
    val isPinned: Boolean = false,
    val remark: String? = null
)

data class BackupEnv(
    val name: String,
    val value: String,
    val remarks: String? = null,
    val enabled: Boolean = true
)

data class BackupScript(
    val path: String,
    val content: String
)

data class BackupConfigFile(
    val path: String,
    val content: String
)

/** 本地脚本库条目（v2）：folder 为虚拟文件夹归属，空表示未分类 */
data class BackupLocalScript(
    val name: String,
    val folder: String = "",
    val content: String
)

/** 面板实例（v2）：含登录凭据，备份文件为明文，需用户自行妥善保管 */
data class BackupPanelInstance(
    val name: String,
    val type: String,
    val baseUrl: String,
    val username: String? = null,
    val password: String? = null,
    val token: String? = null
)

/** 恢复结果逐项报告 */
data class RestoreReport(
    val category: String,
    val total: Int,
    val success: Int,
    val skipped: Int,
    val errors: List<String>
) {
    val failed: Int get() = total - success - skipped
}

/** 把面板数据序列化为可导出的 JSON */
fun buildBackupJson(
    sourcePanelType: String?,
    sourcePanelName: String?,
    tasks: List<BackupTask>,
    envs: List<BackupEnv>,
    scripts: List<BackupScript>,
    configFiles: List<BackupConfigFile>,
    localScripts: List<BackupLocalScript> = emptyList(),
    panels: List<BackupPanelInstance> = emptyList(),
    envSegRules: Map<String, String> = emptyMap()
): String {
    val backup = PanelBackup(
        exportedAt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US).format(Date()),
        sourcePanelType = sourcePanelType,
        sourcePanelName = sourcePanelName,
        tasks = tasks,
        envs = envs,
        scripts = scripts,
        configFiles = configFiles,
        localScripts = localScripts,
        panels = panels,
        envSegRules = envSegRules
    )
    return com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(backup)
}

/** 从 JSON 文本解析备份，失败返回 null */
fun parseBackupJson(json: String): PanelBackup? {
    return try {
        com.google.gson.Gson().fromJson(json, PanelBackup::class.java)
    } catch (_: Exception) {
        null
    }
}

/** 解析后的预览数据，供用户勾选后选择性恢复 */
data class PreviewRestoreData(
    val sourcePanelType: String? = null,
    val sourcePanelName: String? = null,
    val tasks: List<BackupTask> = emptyList(),
    val envs: List<BackupEnv> = emptyList(),
    val scripts: List<BackupScript> = emptyList(),
    val configFiles: List<BackupConfigFile> = emptyList(),
    val localScripts: List<BackupLocalScript> = emptyList(),
    val panels: List<BackupPanelInstance> = emptyList(),
    val envSegRules: Map<String, String> = emptyMap()
) {
    val hasData get() = tasks.isNotEmpty() || envs.isNotEmpty() || scripts.isNotEmpty() ||
            configFiles.isNotEmpty() || localScripts.isNotEmpty() || panels.isNotEmpty() ||
            envSegRules.isNotEmpty()
}
