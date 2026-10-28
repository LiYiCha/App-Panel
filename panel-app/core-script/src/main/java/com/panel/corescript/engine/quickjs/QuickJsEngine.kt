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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * JavaScript 引擎：基于 QuickJS。
 * - console.log/warn/error/info 输出流式回调
 * - 工作目录内所有 .js 文件按相对路径注册为 ES 模块，支持 import "./xxx.js"
 * - CommonJS 依赖：js_deps 内已安装的 npm 包注入 require('包名')，另注入
 *   宿主 $http 网络对象与 request/http/https 兼容 shim（对标青龙脚本生态）
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
                val cjsManifest = buildCjsManifest(request.entryFile.parentFile, request.depsDir, request.entryFile)
                quickJs {
                    defineConsole(request.runId, listener)
                    defineHttp()
                    defineHost(cjsManifest)
                    evaluate<Any?>(
                        CJS_BOOTSTRAP + "\n" + request.entryFile.readText(),
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

    /** 宿主 HTTP 对象：get/post 阻塞调用（脚本线程阻塞等待，与同步脚本语义一致） */
    private fun QuickJs.defineHttp() {
        define("panelHttp") {
            function("get") { args ->
                httpJson("GET", args.getOrNull(0)?.toString() ?: "", args.getOrNull(1)?.toString(), null)
            }
            function("post") { args ->
                httpJson(
                    "POST",
                    args.getOrNull(0)?.toString() ?: "",
                    args.getOrNull(1)?.toString(),
                    args.getOrNull(2)?.toString()
                )
            }
        }
    }

    /** 宿主桥：向 CJS 引导层提供模块清单 */
    private fun QuickJs.defineHost(manifest: JSONObject) {
        val json = manifest.toString()
        define("panelHost") {
            function("cjsManifest") { json }
        }
    }

    /** 执行一次 HTTP 请求，结果为 {status, body} JSON 字符串；网络失败返回 {status:0, error} */
    private fun httpJson(method: String, url: String, headersJson: String?, body: String?): String {
        return try {
            val builder = Request.Builder().url(url)
            if (!headersJson.isNullOrBlank()) {
                val headers = JSONObject(headersJson)
                for (name in headers.keys()) builder.header(name, headers.optString(name))
            }
            when (method) {
                "GET" -> builder.get()
                else -> builder.post(
                    (body ?: "").toRequestBody("application/json; charset=utf-8".toMediaType())
                )
            }
            Http.client.newCall(builder.build()).execute().use { resp ->
                val respBody = resp.body?.string() ?: ""
                JSONObject().put("status", resp.code).put("body", respBody).toString()
            }
        } catch (e: Exception) {
            JSONObject().put("status", 0).put("error", e.message ?: e.javaClass.simpleName).toString()
        }
    }

    /**
     * 构建 CommonJS 模块清单：sources（模块路径→源码）+ mains（包名→入口文件）。
     * - 本地脚本：工作目录内除入口外的 .js 文件以相对路径注册，require('./xxx.js') 懒加载复用
     *   （不可用 addModule：quickjs-kt 在 evaluate 时会执行所有已注册模块的顶层代码）
     * - npm 依赖：js_deps 内已安装包按包名注册
     * - 内置 request/http/https 兼容 shim（基于宿主 panelHttp）
     */
    private fun buildCjsManifest(workDir: File?, depsDir: File?, entryFile: File): JSONObject {
        val sources = JSONObject()
        val mains = JSONObject()

        // 内置 request shim
        sources.put("request/index.js", REQUEST_SHIM_JS)
        mains.put("request", "request/index.js")
        mains.put("http", "request/index.js")
        mains.put("https", "request/index.js")

        // 本地脚本文件（相对路径可 require）
        if (workDir != null && workDir.isDirectory) {
            workDir.walkTopDown()
                .filter { it.isFile && it.extension.equals("js", ignoreCase = true) && it != entryFile }
                .forEach { file ->
                    val rel = file.relativeTo(workDir).invariantSeparatorsPath
                    val code = runCatching { file.readText() }.getOrNull() ?: return@forEach
                    sources.put(rel, code)
                }
        }

        // npm 依赖包
        if (depsDir != null && depsDir.isDirectory) {
            depsDir.listFiles()?.filter { it.isDirectory }?.forEach { pkgDir ->
                val pkgJsonFile = File(pkgDir, "package.json")
                if (!pkgJsonFile.isFile) return@forEach
                val pkgJson = runCatching { JSONObject(pkgJsonFile.readText()) }.getOrNull() ?: return@forEach
                val pkgName = pkgJson.optString("name", pkgDir.name)
                    .takeIf { it.isNotBlank() } ?: pkgDir.name
                if (mains.has(pkgName)) return@forEach

                var main = pkgJson.optString("main", "index.js").trim().removePrefix("./")
                if (main.isEmpty()) main = "index.js"
                val mainFile = File(pkgDir, main)
                val mainPath = when {
                    mainFile.isFile -> main
                    File(pkgDir, "$main.js").isFile -> "$main.js"
                    File(pkgDir, "$main/index.js").isFile -> "$main/index.js"
                    File(pkgDir, "index.js").isFile -> "index.js"
                    else -> null
                }
                if (mainPath != null) mains.put(pkgName, "$pkgName/$mainPath")

                pkgDir.walkTopDown()
                    .filter { it.isFile && (it.extension.equals("js", true) || it.extension.equals("json", true)) }
                    .forEach { file ->
                        val rel = file.relativeTo(pkgDir).invariantSeparatorsPath
                        val code = runCatching { file.readText() }.getOrNull() ?: return@forEach
                        sources.put("$pkgName/$rel", code)
                    }
            }
        }

        return JSONObject().put("sources", sources).put("mains", mains)
    }

    companion object {
        /**
         * CommonJS 引导层：宿主函数 panelHost.cjsManifest() 提供模块清单，
         * 此处实现 require 解析（裸包名 → 包入口；相对路径 → 相对当前文件），
         * 并暴露全局 require / $http（宿主 panelHttp 别名）。
         */
        private const val CJS_BOOTSTRAP = """
;(function () {
  var manifest = JSON.parse(globalThis.panelHost.cjsManifest());
  var sources = manifest.sources;
  var mains = manifest.mains;
  var cache = {};
  globalThis['$' + 'http'] = globalThis.panelHttp;
  function dirOf(p) { var i = p.lastIndexOf('/'); return i < 0 ? '' : p.substring(0, i + 1); }
  function norm(p) {
    var out = [];
    var parts = p.split('/');
    for (var i = 0; i < parts.length; i++) {
      var s = parts[i];
      if (s === '' && out.length !== 0) continue;
      if (s === '.') continue;
      if (s === '..') { out.pop(); continue; }
      out.push(s);
    }
    return out.join('/');
  }
  function tryLoad(candidates) {
    for (var i = 0; i < candidates.length; i++) {
      var p = candidates[i];
      if (sources[p] != null) return loadFile(p);
    }
    return null;
  }
  function loadFile(path) {
    if (cache[path]) return cache[path].exports;
    var src = sources[path];
    if (src == null) throw new Error('模块源码缺失: ' + path);
    var module = { exports: {} };
    cache[path] = module;
    if (path.endsWith('.json')) {
      module.exports = JSON.parse(src);
      return module.exports;
    }
    var dir = dirOf(path);
    var fn = new Function('module', 'exports', 'require', '__filename', src + '\n');
    fn(module, module.exports, function (spec) { resolveFrom(dir, spec); }, path);
    return module.exports;
  }
  function resolveFrom(dir, spec) {
    if (spec.startsWith('./') || spec.startsWith('../')) {
      var p = norm(dir + spec);
      var hit = tryLoad([p, p + '.js', p + '.json', p + '/index.js']);
      if (hit == null) throw new Error('无法解析相对模块: ' + spec);
      return hit;
    }
    var root = spec.startsWith('@')
      ? spec.split('/').slice(0, 2).join('/')
      : spec.split('/')[0];
    var main = mains[root];
    if (main == null) throw new Error('未安装依赖: ' + root + '（请在 本地运行-依赖 中安装）');
    var sub = spec.length > root.length ? spec.substring(root.length + 1) : '';
    if (sub) {
      var sp = norm(root + '/' + sub);
      var sHit = tryLoad([sp, sp + '.js', sp + '.json', sp + '/index.js']);
      if (sHit == null) throw new Error('无法解析子路径模块: ' + spec);
      return sHit;
    }
    return loadFile(main);
  }
  globalThis.require = function (spec) { return resolveFrom('', spec); };
})();
"""

        /** request/http/https 兼容 shim：支持 request(options, callback) 与 request.get/post */
        private const val REQUEST_SHIM_JS = """
var httpx = globalThis['$' + 'http'];
function toHeaders(h) {
  var out = {};
  if (h) for (var k in h) out[k] = String(h[k]);
  return out;
}
function rawCall(method, url, headers, body) {
  var fn = method === 'GET' ? httpx.get : httpx.post;
  var raw = method === 'GET' ? fn(url, JSON.stringify(toHeaders(headers)))
                             : fn(url, JSON.stringify(toHeaders(headers)), body == null ? '' : String(body));
  var res = JSON.parse(raw);
  if (res.error) throw new Error('HTTP 请求失败: ' + res.error);
  return res;
}
function request(options, callback) {
  try {
    if (typeof options === 'string') options = { url: options };
    var method = String(options.method || 'GET').toUpperCase();
    var url = options.url || options.uri;
    if (!url) throw new Error('request 缺少 url');
    var body = options.body;
    if (body != null && typeof body !== 'string') body = JSON.stringify(body);
    var res = rawCall(method, url, options.headers, body);
    var resp = { statusCode: res.status, status: res.status, headers: {}, body: res.body };
    if (typeof callback === 'function') { callback(null, resp, res.body); return resp; }
    return resp;
  } catch (e) {
    if (typeof callback === 'function') { callback(e); return; }
    throw e;
  }
}
request.get = function (url, optionsOrCb, maybeCb) {
  var options = typeof optionsOrCb === 'function' ? { url: url } : Object.assign({ url: url }, optionsOrCb || {});
  return request(options, maybeCb || optionsOrCb);
};
request.post = function (url, optionsOrCb, maybeCb) {
  var options = typeof optionsOrCb === 'function' ? { url: url } : Object.assign({ url: url }, optionsOrCb || {});
  options.method = 'POST';
  return request(options, maybeCb || optionsOrCb);
};
module.exports = request;
"""

        private object Http {
            val client: OkHttpClient = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build()
        }
    }
}
