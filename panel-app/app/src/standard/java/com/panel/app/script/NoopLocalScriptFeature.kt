package com.panel.app.script

import android.app.Application
import androidx.navigation.NavGraphBuilder
import javax.inject.Inject

/** standard 变体的空实现：不启用本地脚本能力，不含引擎依赖 */
class NoopLocalScriptFeature @Inject constructor() : LocalScriptFeature {
    override val enabled: Boolean = false
    override fun init(app: Application) {}
    override fun installRoutes(nav: NavGraphBuilder, openRoute: (String) -> Unit, popBack: () -> Unit) {}
}
