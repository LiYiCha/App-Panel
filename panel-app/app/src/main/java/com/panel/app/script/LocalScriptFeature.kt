package com.panel.app.script

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.navigation.NavGraphBuilder
import com.panel.app.data.backup.RestoreReport

/** 备份/恢复用的本地脚本条目（与变体无关的纯数据载体） */
data class LocalScriptBackupItem(
    val name: String,
    val folder: String = "",
    val content: String
)

/**
 * 本地脚本能力（JS / Python 引擎）的功能开关与装配点。
 * full 变体提供完整实现，standard 变体提供空实现，由 Hilt 按变体绑定。
 */
interface LocalScriptFeature {

    /** 是否启用本地脚本能力（决定设置页入口与导航路由是否生效） */
    val enabled: Boolean

    /** 登录页「本地模式」入口的目标路由；null 表示不显示入口（standard） */
    val localModeRoute: String?

    /** Application 启动时初始化引擎与依赖管理装配 */
    fun init(app: Application)

    /** 在 NavHost 内注册本地脚本相关路由（仅 enabled 时由 MainActivity 调用） */
    fun installRoutes(nav: NavGraphBuilder, openRoute: (String) -> Unit, popBack: () -> Unit)

    /**
     * 本地模式中心页内容（主流程 Dock 内嵌渲染，不带页面壳）。
     * full 渲染脚本/依赖/运行记录三页签；standard 空实现。
     * 编辑器与控制台仍经 local_editor / local_console 路由跳转。
     */
    @Composable
    fun CenterContent(initialTab: Int, onOpenEditor: (String) -> Unit, onOpenConsole: (String) -> Unit)

    /** 本地脚本库条目数（备份页展示用）；standard 返回 null */
    fun backupCount(): Int?

    /** 导出本地脚本库（备份用，含文件夹归属）；standard 返回 null */
    fun exportForBackup(): List<LocalScriptBackupItem>?

    /**
     * 从备份数据恢复本地脚本库：新建或覆盖同名脚本，文件夹归属一并合并。
     * 返回逐项报告；standard 返回 null。
     */
    fun restoreFromBackup(items: List<LocalScriptBackupItem>): RestoreReport?
}
