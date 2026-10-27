package com.panel.corescript.engine

import java.io.File

/** 脚本语言 */
enum class ScriptLang(val extension: String, val label: String) {
    JS("js", "JavaScript"),
    PYTHON("py", "Python");

    companion object {
        /** 根据文件名识别语言，无法识别返回 null */
        fun fromFileName(name: String): ScriptLang? = when {
            name.endsWith(".js", ignoreCase = true) -> JS
            name.endsWith(".py", ignoreCase = true) -> PYTHON
            else -> null
        }
    }
}

/** 单次脚本运行请求 */
data class ScriptRunRequest(
    val runId: String,
    val lang: ScriptLang,
    val entryFile: File,
    /** 工作目录：相对路径基准与本地模块解析根目录 */
    val workDir: File,
    /** Python 运行时依赖目录，非空时注入 sys.path */
    val depsDir: File? = null,
    val args: List<String> = emptyList()
)

/** 输出流类型 */
enum class OutputStream { STDOUT, STDERR }

/** 运行结束方式 */
enum class ExitKind { SUCCESS, FAILED, STOPPED }

/** 引擎输出回调，可能从引擎工作线程调用 */
interface ScriptRunListener {
    fun onOutput(runId: String, stream: OutputStream, text: String)
    fun onFinished(runId: String, exitKind: ExitKind, detail: String?)
}

/** 一次运行的会话句柄 */
interface ScriptSession {
    val runId: String

    /**
     * 请求中断。引擎尽力而为：阻塞中的原生调用返回后才真正退出。
     * 脚本内部可捕获中断异常做清理。
     */
    fun stop()
}

/** 脚本引擎抽象，JS / Python 各一个实现；每次运行创建独立实例，用完关闭 */
interface ScriptEngine : AutoCloseable {
    val lang: ScriptLang
    fun run(request: ScriptRunRequest, listener: ScriptRunListener): ScriptSession
}
