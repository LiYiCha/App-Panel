package com.panel.app.data.adapter

/**
 * 白虎仪表盘辅助函数（提取自 BaihuPanelAdapter）。
 *
 * 包含：formatUptime。
 */
object BaihuDashboardHelpers {

    fun formatUptime(seconds: Long): String = when {
        seconds < 3600 -> "${seconds / 60} 分钟"
        seconds < 86400 -> "${seconds / 3600} 小时 ${(seconds % 3600) / 60} 分"
        else -> "${seconds / 86400} 天 ${(seconds % 86400) / 3600} 小时"
    }
}
