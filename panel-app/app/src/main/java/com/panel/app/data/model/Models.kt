package com.panel.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class PanelType {
    BAIHU,
    QINGLONG_V15,
    QINGLONG_V10
}

@Entity(tableName = "panel_instances")
data class PanelInstance(
    @PrimaryKey val id: String,
    val name: String,
    val type: PanelType,
    val baseUrl: String,
    val username: String? = null,
    val password: String? = null,
    val token: String? = null,
    val isLocalServer: Boolean = false,
    val cpuUsage: String = "--",
    val ramUsage: String = "--"
)

data class UnifiedTask(
    val id: String,
    val name: String,
    val command: String,
    val schedule: String,
    val statusText: String,
    val isRunning: Boolean = false,
    val isDisabled: Boolean = false,
    val isPinned: Boolean = false,
    val labels: List<String> = emptyList(),
    val lastRunningTime: Long? = null,
    val lastExecutionTime: Long? = null,
    val timeout: Int = 30,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val pid: Int? = null,
    val selected: Boolean = false
)

// 订阅/仓库同步模型 (全对齐青龙订阅与白虎仓库同步)
data class UnifiedSubscription(
    val id: String,
    val name: String,
    val type: String = "public-repo", // public-repo, private-repo, file
    val url: String,
    val branch: String = "main",
    val schedule: String = "0 0 * * *",
    val whitelist: String = "",       // 白名单
    val blacklist: String = "",       // 黑名单
    val dependences: String = "",     // 依赖文件
    val extensions: String = "",      // 脚本后缀
    val alias: String = "",           // 唯一别名
    val autoAddCron: Boolean = true,  // 自动添加定时任务
    val autoDelCron: Boolean = true,  // 自动删除失效任务
    val statusText: String = "就绪",
    val isRunning: Boolean = false,
    val isDisabled: Boolean = false,
    val lastRunTime: String? = null,
    val nextRunTime: String? = null,
    val languages: List<String> = emptyList(),
    val location: String = "本地",
    val selected: Boolean = false
)

// 任务历次执行历史实例模型
data class TaskInstanceRecord(
    val id: String,
    val taskName: String = "",
    val startTime: String,
    val endTime: String? = null,
    val duration: String = "",
    val exitCode: Int = 0,
    val statusText: String = "成功",
    val logSnippet: String = "",
    val logPath: String? = null
)

data class UnifiedEnv(
    val id: String,
    val name: String,
    val value: String, // 完整明文展示，不使用 ******** 遮挡
    val remarks: String? = null,
    val enabled: Boolean = true,
    val selected: Boolean = false
)

data class UnifiedDep(
    val id: String,
    val name: String,
    val version: String,
    val type: String, // "python3", "nodejs", "linux"
    val remarks: String? = null,
    val status: Int = 1, // 0: 安装中, 1: 已安装, 2: 安装失败, 3: 卸载中
    val log: String? = null,
    val selected: Boolean = false
)

data class ScriptNode(
    val name: String,
    val path: String,
    val isDir: Boolean,
    val size: String? = null,
    val mtime: Long? = null,
    val content: String? = null,
    val isOpen: Boolean = false,
    val children: List<ScriptNode>? = null
)

data class UnifiedConfigFile(
    val name: String,
    val path: String,
    val content: String
)

/**
 * 正在运行的任务实例。
 * 青龙从 `GET /api/dashboard/runtime` 获取（带 instanceId，可精确停止单个实例）；
 * 白虎从 `GET /api/v1/monitor` 的 scheduler.workers 获取（只有 task_id，需再解析出 logID 才能停止）。
 */
data class RunningTaskInfo(
    val taskId: String,
    val name: String,
    /** 青龙的运行实例 ID；白虎没有这个概念，为 null */
    val instanceId: String? = null,
    val pid: Int? = null,
    val elapsedSeconds: Long? = null,
    val logPath: String? = null
)

/**
 * 面板仪表盘数据（两个面板字段对齐后的统一视图）。
 * 各面板支持的字段不同：缺的留 null，UI 据此隐藏对应区块，不要伪造 0。
 */
data class PanelDashboard(
    // 概览
    val totalTasks: Int? = null,
    val enabledTasks: Int? = null,
    val disabledTasks: Int? = null,
    val todayRuns: Long? = null,
    val todaySuccess: Long? = null,
    val todayFail: Long? = null,
    val successRate: String? = null,
    val avgTimeMs: Long? = null,
    // 白虎补充的总量类指标
    val totalEnvs: Long? = null,
    val totalLogs: Long? = null,
    val scheduledCount: Int? = null,
    val runningCount: Int? = null,
    // 趋势
    val trend: List<TrendPoint> = emptyList(),
    // 排行
    val topByCount: List<TaskRank> = emptyList(),
    val topByTime: List<TaskRank> = emptyList(),
    // 标签统计（青龙）
    val labelStats: List<LabelStat> = emptyList(),
    // 资源
    val cpuUsage: String? = null,
    val memUsage: String? = null,
    val resourceDetail: Map<String, String> = emptyMap()
)

/** 单日执行趋势 */
data class TrendPoint(
    val date: String,
    val total: Int,
    val success: Int,
    val fail: Int
)

/** 排行榜条目，value 为展示用主指标文本 */
data class TaskRank(
    val rank: Int,
    val name: String,
    val value: String,
    val detail: String? = null
)

/** 按标签聚合的任务统计（青龙） */
data class LabelStat(
    val label: String,
    val count: Int,
    val todayRuns: Int = 0,
    val successRate: String? = null,
    val avgTimeMs: Long? = null
)

fun List<ScriptNode>.extractScriptFiles(): List<String> {
    val result = mutableListOf<String>()
    fun traverse(list: List<ScriptNode>) {
        for (node in list) {
            if (!node.isDir) {
                result.add(node.path)
            } else if (node.children != null) {
                traverse(node.children)
            }
        }
    }
    traverse(this)
    return result
}

