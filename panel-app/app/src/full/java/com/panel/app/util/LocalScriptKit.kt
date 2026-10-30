package com.panel.app.util

import android.content.Context
import com.panel.app.data.logger.AppLogger
import com.panel.app.data.logger.LogLevel
import com.panel.corescript.deps.JsDepManager
import com.panel.corescript.deps.PyDepManager
import com.panel.corescript.runner.ScriptRunBus
import com.panel.corescript.store.LocalScriptStore
import com.panel.corescript.store.RunLogStore
import java.io.File

/**
 * 本地脚本能力集：引擎层单例的装配点，在 Application.onCreate 初始化一次。
 * 运行事件同步写入 AppLogger，开发者控制台可见。
 */
object LocalScriptKit {

    lateinit var store: LocalScriptStore
        private set

    lateinit var pyDeps: PyDepManager
        private set

    /** JS 运行时依赖（npm 纯 JS 包） */
    lateinit var jsDeps: JsDepManager
        private set

    /** 运行记录管理（历史日志列表/回放/删除） */
    fun runLogStore(context: Context): RunLogStore =
        RunLogStore(File(context.filesDir, "run_logs"))

    fun init(context: Context) {
        store = LocalScriptStore(File(context.filesDir, "local_scripts"))
        pyDeps = PyDepManager(
            File(context.filesDir, "py_deps"),
            mirrorProvider = {
                // 每次下载时动态取当前 PyPI 镜像源（设置页改动即时生效）
                MirrorManager.selectedPyPiOrigin()
            }
        )
        jsDeps = JsDepManager(
            File(context.filesDir, "js_deps"),
            mirrorProvider = {
                // 每次下载时动态取当前 npm 镜像源（设置页改动即时生效）
                MirrorManager.selectedNpmOrigin()
            }
        )
        ScriptRunBus.externalLogger = { level, tag, message ->
            AppLogger.log(
                if (level == "ERROR") LogLevel.ERROR else LogLevel.INFO,
                tag,
                message
            )
        }
    }
}
