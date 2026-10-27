package com.panel.corescript.engine.python

import android.content.Context
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.panel.corescript.engine.ExitKind
import com.panel.corescript.engine.OutputStream
import com.panel.corescript.engine.ScriptEngine
import com.panel.corescript.engine.ScriptLang
import com.panel.corescript.engine.ScriptRunListener
import com.panel.corescript.engine.ScriptRunRequest
import com.panel.corescript.engine.ScriptSession
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Python 引擎：基于 Chaquopy（CPython 3.13）。
 * - stdout/stderr 重定向为流式回调
 * - depsDir 注入 sys.path（运行时安装的纯 Python 包）
 * - 中断：通过 sys.settrace 逐行检查停止标志，触发 SystemExit；
 *   阻塞中的 C 扩展调用返回后才会检查到
 */
class PythonEngine(context: Context) : ScriptEngine {

    override val lang = ScriptLang.PYTHON

    init {
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(context.applicationContext))
        }
    }

    override fun run(request: ScriptRunRequest, listener: ScriptRunListener): ScriptSession {
        val stopFlag = AtomicBoolean(false)
        val finished = AtomicBoolean(false)

        val runnerThread = thread(name = "py-run-${request.runId.take(8)}") {
            var exitKind = ExitKind.SUCCESS
            var detail: String? = null
            try {
                val py = Python.getInstance()
                val exitCode = py.getModule("py_runner").callAttr(
                    "run_script",
                    request.entryFile.absolutePath,
                    request.workDir.absolutePath,
                    request.depsDir?.absolutePath,
                    request.args.toTypedArray(),
                    listener,
                    request.runId,
                    OutputStream.STDOUT,
                    OutputStream.STDERR,
                    StopFlag { stopFlag.get() }
                ).toInt()
                when {
                    exitCode == 0 -> exitKind = ExitKind.SUCCESS
                    exitCode == 130 -> exitKind = ExitKind.STOPPED
                    else -> {
                        exitKind = ExitKind.FAILED
                        detail = "退出码 $exitCode"
                    }
                }
            } catch (t: Throwable) {
                exitKind = ExitKind.FAILED
                detail = t.message ?: t.javaClass.simpleName
            } finally {
                finished.set(true)
                listener.onFinished(request.runId, exitKind, detail)
            }
        }

        return object : ScriptSession {
            override val runId = request.runId
            override fun stop() {
                if (!stopFlag.compareAndSet(false, true)) return
                // 停止信号靠 settrace 逐行生效；脚本阻塞在 C 扩展时无法立即退出，
                // 延迟后仍存活则向用户说明原因，避免"点了停止没反应"的困惑
                thread(name = "py-stop-watchdog-${request.runId.take(8)}", isDaemon = true) {
                    Thread.sleep(3000)
                    if (!finished.get() && runnerThread.isAlive) {
                        listener.onOutput(
                            request.runId,
                            OutputStream.STDERR,
                            "停止信号已发出；脚本阻塞在原生调用（如 time.sleep、网络请求）中，将在其返回后退出"
                        )
                    }
                }
            }
        }
    }

    /** 解释器为进程级单例，引擎无独立资源 */
    override fun close() {}

    /** 供 Python 侧逐行轮询的停止标志 */
    class StopFlag(private val provider: () -> Boolean) {
        @Suppress("unused")
        fun isStopped(): Boolean = provider()
    }
}
