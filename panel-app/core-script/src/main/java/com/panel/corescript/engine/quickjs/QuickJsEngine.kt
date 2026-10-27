package com.panel.corescript.engine.quickjs

import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.QuickJsException
import com.dokar.quickjs.binding.define
import com.dokar.quickjs.quickJs
import com.panel.corescript.engine.ExitKind
import com.panel.corescript.engine.OutputStream
import com.panel.corescript.engine.ScriptEngine
import com.panel.corescript.engine.ScriptLang
import com.panel.corescript.engine.ScriptRunListener
import com.panel.corescript.engine.ScriptRunRequest
import com.panel.corescript.engine.ScriptSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import kotlin.concurrent.thread

/**
 * JavaScript 引擎：基于 QuickJS。
 * - console.log/warn/error/info 输出流式回调
 * - 工作目录内所有 .js 文件按相对路径注册为 ES 模块，支持 import "./xxx.js"
 * - 中断：异步挂起点立即生效；无挂起点的纯同步死循环走看门狗强制上报
 */
class QuickJsEngine : ScriptEngine {

    override val lang = ScriptLang.JS

    @Volatile
    private var finished = false

    override fun run(request: ScriptRunRequest, listener: ScriptRunListener): ScriptSession {
        finished = false

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        scope.launch {
            var exitKind = ExitKind.SUCCESS
            var detail: String? = null
            try {
                quickJs {
                    defineConsole(request.runId, listener)
                    registerLocalModules(request)
                    evaluate<Any?>(
                        request.entryFile.readText(),
                        filename = request.entryFile.name,
                        asModule = true
                    )
                }
            } catch (e: CancellationException) {
                exitKind = ExitKind.STOPPED
            } catch (e: QuickJsException) {
                exitKind = ExitKind.FAILED
                detail = e.message
            } catch (e: Throwable) {
                exitKind = ExitKind.FAILED
                detail = e.message ?: e.javaClass.simpleName
            } finally {
                finished = true
                listener.onFinished(request.runId, exitKind, detail)
            }
        }

        return object : ScriptSession {
            override val runId = request.runId
            override fun stop() {
                scope.cancel()
                // 纯同步脚本无挂起点，取消无法打断原生执行；超时后直接上报停止，
                // 卡死的执行线程随进程存续，不再关闭引擎避免崩溃
                thread(name = "js-stop-watchdog", isDaemon = true) {
                    repeat(30) {
                        if (finished) return@thread
                        Thread.sleep(100)
                    }
                    if (!finished) {
                        finished = true
                        listener.onFinished(request.runId, ExitKind.STOPPED, "已强制停止（脚本无异步挂起点）")
                    }
                }
            }
        }
    }

    /** 实例随每次运行创建与销毁，无全局资源需要释放 */
    override fun close() {}

    private fun QuickJs.defineConsole(runId: String, listener: ScriptRunListener) {
        fun emit(stream: OutputStream) = { args: Array<Any?> ->
            listener.onOutput(runId, stream, args.joinToString(" ") { it?.toString() ?: "undefined" })
        }
        define("console") {
            function("log") { emit(OutputStream.STDOUT)(it) }
            function("info") { emit(OutputStream.STDOUT)(it) }
            function("warn") { emit(OutputStream.STDERR)(it) }
            function("error") { emit(OutputStream.STDERR)(it) }
        }
    }

    /** 工作目录内所有 .js 文件注册为模块：以 "./相对路径" 与裸相对路径两种名字注册 */
    private fun QuickJs.registerLocalModules(request: ScriptRunRequest) {
        val workDir = request.workDir
        workDir.walkTopDown()
            .filter { it.isFile && it.extension.equals("js", ignoreCase = true) && it != request.entryFile }
            .forEach { file ->
                val rel = file.relativeTo(workDir).invariantSeparatorsPath
                val code = runCatching { file.readText() }.getOrNull() ?: return@forEach
                addModule(name = "./$rel", code = code)
                addModule(name = rel, code = code)
            }
    }
}
