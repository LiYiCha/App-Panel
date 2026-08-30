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
    val content: String? = null,
    val isOpen: Boolean = false,
    val children: List<ScriptNode>? = null
)

data class UnifiedConfigFile(
    val name: String,
    val path: String,
    val content: String
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

fun getStandardConfigTemplate(panelType: PanelType): String {
    return when (panelType) {
        PanelType.BAIHU -> """
            {
              "server": {
                "port": 5700,
                "host": "0.0.0.0"
              },
              "cron": {
                "max_concurrent": 10,
                "timeout": 3600
              },
              "log": {
                "level": "info",
                "compression": "zstd"
              }
            }
        """.trimIndent()
        else -> """
            ## 青龙面板全局配置文件 config.sh
            ## 系统环境变量与拉库规则配置
            
            export RepoUrl="https://github.com/sample/repo.git"
            export RandomDelay="300"
            export AutoDelCron="true"
            export CommandTimeoutTime="1h"
            export NotifyTitle="青龙资产变动通知"
        """.trimIndent()
    }
}

