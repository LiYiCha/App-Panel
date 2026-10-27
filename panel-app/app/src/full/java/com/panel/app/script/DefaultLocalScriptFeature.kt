package com.panel.app.script

import android.app.Application
import android.net.Uri
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.panel.app.ui.screens.LocalDepsScreen
import com.panel.app.ui.screens.LocalRunConsoleScreen
import com.panel.app.ui.screens.LocalScriptEditorScreen
import com.panel.app.ui.screens.LocalScriptsScreen
import com.panel.app.util.LocalScriptKit
import javax.inject.Inject

/** full 变体的完整实现：装配引擎、注册本地脚本路由 */
class DefaultLocalScriptFeature @Inject constructor() : LocalScriptFeature {

    override val enabled: Boolean = true

    override fun init(app: Application) {
        LocalScriptKit.init(app)
    }

    override fun installRoutes(nav: NavGraphBuilder, openRoute: (String) -> Unit, popBack: () -> Unit) {
        nav.composable(ROUTE_SCRIPTS) {
            LocalScriptsScreen(
                onBack = popBack,
                onOpenEditor = { name -> openRoute("local_editor/${Uri.encode(name)}") },
                onOpenConsole = { runId -> openRoute("local_console/$runId") },
                onOpenDeps = { openRoute(ROUTE_DEPS) }
            )
        }
        nav.composable(
            route = "local_editor/{name}",
            arguments = listOf(navArgument("name") { type = NavType.StringType })
        ) { entry ->
            LocalScriptEditorScreen(
                scriptName = Uri.decode(entry.arguments?.getString("name") ?: ""),
                onBack = popBack,
                onOpenConsole = { runId -> openRoute("local_console/$runId") }
            )
        }
        nav.composable(
            route = "local_console/{runId}",
            arguments = listOf(
                navArgument("runId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { entry ->
            LocalRunConsoleScreen(
                runId = entry.arguments?.getString("runId"),
                onBack = popBack
            )
        }
        nav.composable(ROUTE_DEPS) {
            LocalDepsScreen(onBack = popBack)
        }
    }

    companion object {
        const val ROUTE_SCRIPTS = "local_scripts"
        const val ROUTE_DEPS = "local_deps"
    }
}
