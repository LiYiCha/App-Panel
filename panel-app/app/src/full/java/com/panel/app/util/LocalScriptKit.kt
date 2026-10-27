package com.panel.app.util

import android.content.Context
import com.panel.app.data.logger.AppLogger
import com.panel.app.data.logger.LogLevel
import com.panel.corescript.deps.PyDepManager
import com.panel.corescript.runner.ScriptRunBus
import com.panel.corescript.store.LocalScriptStore
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

    fun init(context: Context) {
        store = LocalScriptStore(File(context.filesDir, "local_scripts"))
        pyDeps = PyDepManager(
            File(context.filesDir, "py_deps"),
            mirrorProvider = {
                // 每次下载时动态取当前 PyPI 镜像源（设置页改动即时生效）
                MirrorManager.selectedPyPiOrigin()
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
