package com.panel.app.script

import android.app.Application
import androidx.navigation.NavGraphBuilder

/**
 * 本地脚本能力（JS / Python 引擎）的功能开关与装配点。
 * full 变体提供完整实现，standard 变体提供空实现，由 Hilt 按变体绑定。
 */
interface LocalScriptFeature {

    /** 是否启用本地脚本能力（决定设置页入口与导航路由是否生效） */
    val enabled: Boolean

    /** Application 启动时初始化引擎与依赖管理装配 */
    fun init(app: Application)

    /** 在 NavHost 内注册本地脚本相关路由（仅 enabled 时由 MainActivity 调用） */
    fun installRoutes(nav: NavGraphBuilder, openRoute: (String) -> Unit, popBack: () -> Unit)
}
