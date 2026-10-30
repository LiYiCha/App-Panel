package com.panel.app.script

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.panel.app.data.backup.RestoreReport
import com.panel.app.ui.screens.LocalRunConsoleScreen
import com.panel.app.ui.screens.LocalScriptEditorScreen
import com.panel.app.ui.screens.ScriptCenterScreen
import com.panel.app.util.LocalScriptKit
import com.panel.corescript.engine.ScriptLang
import com.panel.corescript.store.LocalScriptStore
import javax.inject.Inject

/** full 变体的完整实现：装配引擎、注册本地脚本路由（脚本中心单页 + 编辑器 + 控制台） */
class DefaultLocalScriptFeature @Inject constructor() : LocalScriptFeature {

    override val enabled: Boolean = true

    override val localModeRoute: String? = ROUTE_SCRIPTS

    override fun init(app: Application) {
        LocalScriptKit.init(app)
    }

    override fun installRoutes(nav: NavGraphBuilder, openRoute: (String) -> Unit, popBack: () -> Unit) {
        // 脚本中心（Tab：脚本 | 依赖 | 运行记录）；?tab 支持直达指定页签
        nav.composable(
            route = "local_center?tab={tab}",
            arguments = listOf(
                navArgument("tab") {
                    type = NavType.IntType
                    defaultValue = 0
                }
            )
        ) { entry ->
            ScriptCenterScreen(
                initialTab = entry.arguments?.getInt("tab") ?: 0,
                onBack = popBack,
                onOpenEditor = { name -> openRoute("local_editor/${Uri.encode(name)}") },
                onOpenConsole = { runId -> openRoute("local_console/$runId") }
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
    }

    @Composable
    override fun CenterContent(initialTab: Int, onOpenEditor: (String) -> Unit, onOpenConsole: (String) -> Unit) {
        ScriptCenterScreen(
            initialTab = initialTab,
            embedded = true,
            onOpenEditor = onOpenEditor,
            onOpenConsole = onOpenConsole
        )
    }

    companion object {
        const val ROUTE_CENTER = "local_center?tab={tab}"

        /** 供外部跳转用的便捷路由（默认脚本 Tab） */
        const val ROUTE_SCRIPTS = "local_center"
    }

    // ---------- 备份与恢复（设置页「备份与恢复」联动） ----------

    override fun backupCount(): Int = LocalScriptKit.store.list().size

    override fun exportForBackup(): List<LocalScriptBackupItem> =
        LocalScriptKit.store.list().map { script ->
            LocalScriptBackupItem(
                name = script.name,
                folder = script.folder,
                content = LocalScriptKit.store.read(script.file).getOrDefault("")
            )
        }

    override fun restoreFromBackup(items: List<LocalScriptBackupItem>): RestoreReport {
        var success = 0
        val errors = mutableListOf<String>()
        val folderMap = mutableMapOf<String, String>()
        items.forEach { item ->
            val lang = ScriptLang.fromFileName(item.name)
            val normalized = lang?.let { LocalScriptStore.normalizeName(item.name, it) }
            if (normalized == null) {
                errors.add("${item.name}: 文件名不合法")
                return@forEach
            }
            runCatching {
                val file = LocalScriptKit.store.fileFor(normalized)
                if (file.exists()) {
                    file.writeText(item.content)
                } else {
                    LocalScriptKit.store.create(normalized, item.content, lang).getOrThrow()
                }
            }.onSuccess {
                success++
                if (item.folder.isNotBlank()) folderMap[normalized] = item.folder
            }.onFailure { e ->
                errors.add("${item.name}: ${e.message ?: "写入失败"}")
            }
        }
        // 文件全部落盘后再合并文件夹归属（本次恢复与已存在的同名脚本都生效）
        if (folderMap.isNotEmpty()) LocalScriptKit.store.mergeFolders(folderMap)
        return RestoreReport("本地脚本库", items.size, success, 0, errors)
    }
}
