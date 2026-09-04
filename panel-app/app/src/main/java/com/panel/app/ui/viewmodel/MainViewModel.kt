package com.panel.app.ui.viewmodel

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.panel.app.data.adapter.BaihuPanelAdapter
import com.panel.app.data.adapter.QinglongV10Adapter
import com.panel.app.data.adapter.QinglongV15Adapter
import com.panel.app.data.remote.api.QinglongV15Api
import com.panel.app.data.backup.*
import com.panel.app.data.model.*
import com.panel.app.data.model.RunningTaskInfo
import com.panel.app.data.remote.OtpRequiredException
import com.panel.app.data.repository.PanelRepository
import com.panel.app.ui.screens.BottomNavScreen
import com.panel.app.util.PanelUrl
import com.panel.app.util.runSafely
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import javax.inject.Inject

/**
 * 刷新范围。
 *
 * 一次全量刷新会并发 7 个接口 + 串行读配置，青龙侧还会因脚本树逐目录补拉产生 N+1，
 * 实测一次可达 8+N 次请求。而绝大多数写操作（如保存一个环境变量、置顶一个任务）
 * 只影响其中一类数据，没必要把任务/订阅/变量/依赖/脚本树/配置/监控全部重打一遍。
 *
 * 因此写操作按需声明自己真正影响的类别，未声明的类别沿用内存中的既有数据。
 */
enum class RefreshScope { TASKS, SUBS, ENVS, DEPS, SCRIPTS, CONFIG, METRICS }

data class MainUiState(
    val isDarkTheme: Boolean = false,
    val selectedPanelIndex: Int = 0,
    val currentTab: BottomNavScreen = BottomNavScreen.Tasks,
    val panels: List<PanelInstance> = emptyList(),
    val tasks: List<UnifiedTask> = emptyList(),
    val subscriptions: List<UnifiedSubscription> = emptyList(),
    val envs: List<UnifiedEnv> = emptyList(),
    val scriptTree: List<ScriptNode> = emptyList(),
    val configFiles: List<String> = listOf("config.sh"),
    val selectedConfigFile: String = "config.sh",
    val configContent: String = "",
    val deps: List<UnifiedDep> = emptyList(),
    val activeLogContent: String = "",
    val activeTaskInstances: List<TaskInstanceRecord> = emptyList(),
    val isLoading: Boolean = false,
    val isAuthenticating: Boolean = false,
    val isDevMode: Boolean = false,
    val isTaskBatchMode: Boolean = false,
    val isEnvBatchMode: Boolean = false,
    val isDepBatchMode: Boolean = false,
    val isDatabaseReady: Boolean = false,
    val expandedScriptFolders: Set<String> = emptySet(),
    val expandedHistoryScripts: Set<String> = emptySet(),
    val toastMessage: String? = null,
    /** 非空表示该面板开启了两步验证，等待用户输入动态验证码 */
    val otpPendingPanel: PanelInstance? = null,
    /** 脚本内容缓存：路径 -> 内容 */
    val scriptViewerCache: Map<String, String> = emptyMap(),
    val scriptViewerLoadingPath: String? = null
)

data class CachedPanelData(
    val tasks: List<UnifiedTask> = emptyList(),
    val envs: List<UnifiedEnv> = emptyList(),
    val subscriptions: List<UnifiedSubscription> = emptyList(),
    val deps: List<UnifiedDep> = emptyList(),
    val scriptTree: List<ScriptNode> = emptyList(),
    val configFiles: List<String> = emptyList(),
    val selectedConfigFile: String = "config.sh",
    val configContent: String = ""
)

@HiltViewModel
class MainViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: PanelRepository
) : ViewModel() {

    private val prefs: SharedPreferences = context.getSharedPreferences("panel_hub_prefs", Context.MODE_PRIVATE)
    private val gson = com.google.gson.Gson()

    // 标记是否已发起过网络请求获取数据
    private var hasNetworkDataLoaded = false

    /** 仪表盘上次成功拉取时间 panelId -> 时间戳，配合 TTL 复用缓存 */
    private val dashboardFetchedAt = mutableMapOf<String, Long>()
    private val dashboardTtlMs = 60_000L

    private fun loadDiskCache(panelId: String) {
        try {
            val file = java.io.File(context.filesDir, "cache/panel_cache_${panelId}.json")
            if (file.exists()) {
                val json = file.readText()
                val root = JsonParser.parseString(json).asJsonObject
                fun <T> readList(name: String, type: java.lang.reflect.Type): List<T> =
                    root.get(name)?.let { gson.fromJson<List<T>>(it, type) }.orEmpty()
                val data = CachedPanelData(
                    tasks = readList("tasks", object : TypeToken<List<UnifiedTask>>() {}.type),
                    envs = readList("envs", object : TypeToken<List<UnifiedEnv>>() {}.type),
                    subscriptions = readList("subscriptions", object : TypeToken<List<UnifiedSubscription>>() {}.type),
                    deps = readList("deps", object : TypeToken<List<UnifiedDep>>() {}.type),
                    scriptTree = readList("scriptTree", object : TypeToken<List<ScriptNode>>() {}.type),
                    configFiles = readList("configFiles", object : TypeToken<List<String>>() {}.type),
                    selectedConfigFile = root.get("selectedConfigFile")?.asString ?: "config.sh",
                    configContent = root.get("configContent")?.asString ?: ""
                )
                // 只有从未加载过网络数据时，才使用缓存作为初始数据
                if (data != null && getActivePanel().id == panelId && !hasNetworkDataLoaded) {
                    _uiState.value = _uiState.value.copy(
                        tasks = data.tasks,
                        envs = data.envs,
                        subscriptions = data.subscriptions,
                        deps = data.deps,
                        scriptTree = data.scriptTree,
                        configFiles = data.configFiles,
                        selectedConfigFile = data.selectedConfigFile,
                        configContent = data.configContent
                    )
                }
            }
        } catch (_: Exception) {}
    }

    private fun saveDiskCache(
        panelId: String,
        tasks: List<UnifiedTask>,
        envs: List<UnifiedEnv>,
        subs: List<UnifiedSubscription>,
        deps: List<UnifiedDep>,
        scriptTree: List<ScriptNode>,
        configFiles: List<String>,
        selectedConfigFile: String,
        configContent: String
    ) {
        try {
            java.io.File(context.filesDir, "cache").mkdirs()
            val file = java.io.File(context.filesDir, "cache/panel_cache_${panelId}.json")
            val data = CachedPanelData(tasks, envs, subs, deps, scriptTree, configFiles, selectedConfigFile, configContent)
            file.writeText(gson.toJson(data))
        } catch (_: Exception) {}
    }

    private val _uiState = MutableStateFlow(
        MainUiState(
            isDarkTheme = prefs.getBoolean("is_dark_theme", false),
            isDevMode = prefs.getBoolean("is_dev_mode", false)
        )
    )
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        com.panel.app.data.logger.AppLogger.isDevModeEnabled = _uiState.value.isDevMode
        initData()
    }

    fun getActivePanel(): PanelInstance {
        val list = _uiState.value.panels
        val savedActiveId = prefs.getString("active_panel_id", null)
        if (savedActiveId != null) {
            val matched = list.firstOrNull { it.id == savedActiveId }
            if (matched != null) return matched
        }
        val idx = _uiState.value.selectedPanelIndex.coerceIn(0, maxOf(list.size - 1, 0))
        return list.getOrNull(idx) ?: PanelInstance(
            "baihu-local", "白虎面板", PanelType.BAIHU, "http://127.0.0.1:5700", isLocalServer = true
        )
    }

    private fun initData() {
        viewModelScope.launch {
            repository.panelsFlow.collect { panelList ->
                // 彻底清理无账号密码且无 token 的虚假/历史残留面板
                val validPanels = panelList.filter {
                    !it.token.isNullOrEmpty() || (!it.username.isNullOrEmpty() && !it.password.isNullOrEmpty())
                }
                if (validPanels.size != panelList.size) {
                    panelList.filter { it.token.isNullOrEmpty() && it.username.isNullOrEmpty() && it.password.isNullOrEmpty() }
                        .forEach { repository.deletePanel(it.id) }
                }

                if (validPanels.isNotEmpty()) {
                    val savedActiveId = prefs.getString("active_panel_id", null)
                    val targetIdx = if (savedActiveId != null) {
                        validPanels.indexOfFirst { it.id == savedActiveId }.takeIf { it >= 0 } ?: 0
                    } else {
                        _uiState.value.selectedPanelIndex.coerceIn(0, validPanels.size - 1)
                    }
                    val activePanel = validPanels[targetIdx]
                    val previousActiveId = _uiState.value.panels.getOrNull(_uiState.value.selectedPanelIndex)?.id
                    val isPanelChanged = previousActiveId != null && previousActiveId != activePanel.id

                    _uiState.value = _uiState.value.copy(
                        isDatabaseReady = true,
                        panels = validPanels,
                        selectedPanelIndex = targetIdx,
                        tasks = if (isPanelChanged) emptyList() else _uiState.value.tasks,
                        subscriptions = if (isPanelChanged) emptyList() else _uiState.value.subscriptions,
                        envs = if (isPanelChanged) emptyList() else _uiState.value.envs,
                        deps = if (isPanelChanged) emptyList() else _uiState.value.deps,
                        scriptTree = if (isPanelChanged) emptyList() else _uiState.value.scriptTree
                    )
                    // 1. 先恢复磁盘缓存（IO线程），让 UI 立刻显示上次的数据
                    viewModelScope.launch(Dispatchers.IO) {
                        loadDiskCache(activePanel.id)
                        // 2. 缓存恢复完成后，在后台异步发起网络同步（不阻塞 UI）
                        if (!hasNetworkDataLoaded) {
                            refreshPanelRemoteData(activePanel)
                        }
                    }
                } else {
                    _uiState.value = _uiState.value.copy(
                        isDatabaseReady = true,
                        panels = emptyList(),
                        selectedPanelIndex = 0,
                        tasks = emptyList(),
                        subscriptions = emptyList(),
                        envs = emptyList(),
                        deps = emptyList(),
                        scriptTree = emptyList()
                    )
                }
            }
        }
    }

    fun selectTab(tab: BottomNavScreen) {
        _uiState.value = _uiState.value.copy(currentTab = tab)
    }

    fun toggleTheme() {
        val newTheme = !_uiState.value.isDarkTheme
        prefs.edit { putBoolean("is_dark_theme", newTheme) }
        _uiState.value = _uiState.value.copy(isDarkTheme = newTheme)
    }

    fun switchPanel(index: Int) {
        val panels = _uiState.value.panels
        if (index in panels.indices) {
            val targetPanel = panels[index]
            setActivePanelId(targetPanel.id)
            _uiState.value = _uiState.value.copy(
                selectedPanelIndex = index,
                tasks = emptyList(),
                subscriptions = emptyList(),
                envs = emptyList(),
                deps = emptyList(),
                scriptTree = emptyList(),
                configFiles = emptyList(),
                selectedConfigFile = "config.sh",
                configContent = ""
            )
            hasNetworkDataLoaded = false
            viewModelScope.launch(Dispatchers.IO) {
                loadDiskCache(targetPanel.id)
                if (!hasNetworkDataLoaded) {
                    refreshPanelRemoteData(targetPanel)
                }
            }
        }
    }

    /**
     * 调用一个面板接口，把"抛异常"和"返回失败 Result"统一成同一个失败 [Result]。
     *
     * 适配器方法声明上都是"不抛异常的"，但两处例外会让它违约：
     * Gson 把 null 塞进 Kotlin 非空字段后读取时的 NPE、构造 Retrofit 时的
     * IllegalArgumentException 等。异常一旦冒出 `await()`，
     * 而 `viewModelScope.launch` 没有异常处理器，就会走到 Thread 的
     * UncaughtExceptionHandler —— 直接闪退。
     */
    private suspend fun <T> safeCall(block: suspend () -> Result<T>): Result<T> =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }

    /** [safeCall] 的并发版，用于一次并发拉起全部核心接口 */
    private fun <T> CoroutineScope.safeAsync(block: suspend () -> Result<T>): Deferred<Result<T>> =
        async { safeCall(block) }

    /**
     * 写入"当前工作面板" id。
     * 必须用 `commit = true` 同步落盘：`SharedPreferences.edit {}` 默认是 `apply()`（异步），
     * 写盘完成前 Room 的 panelsFlow 可能已经推送新一轮面板列表，
     * 此时 `initData` 读到的仍是旧 id，会拿着**上一个面板**去刷新
     * （现象就是：刚登录青龙，控制台却在报"失败去连接 118.x.x.x"这个白虎地址）。
     */
    private fun setActivePanelId(panelId: String) {
        prefs.edit(commit = true) { putString("active_panel_id", panelId) }
    }

    /**
     * 拉取面板远端数据。
     *
     * @param scopes 需要刷新的数据类别；传 null（默认）表示全量刷新。
     *               写操作应只传自己真正影响的类别，避免无谓的全量请求。
     */
    fun refreshPanelRemoteData(
        panel: PanelInstance,
        notifyOnNoCredential: Boolean = false,
        scopes: Set<RefreshScope>? = null
    ) {
        viewModelScope.launch {
            // 若面板无凭证，尝试使用已存账号密码静默认证；若无凭据直接停止，避免发空请求被 401 拦截
            var activePanel = panel
            if (activePanel.token.isNullOrEmpty()) {
                if (!activePanel.username.isNullOrEmpty() && !activePanel.password.isNullOrEmpty()) {
                    // 静默重登：这里的异常同样要就地收敛，否则冒到主线程就是闪退
                    val authRes = runSafely { repository.testAuthenticate(activePanel) }
                        .getOrElse { Result.failure(it) }
                    if (authRes.isSuccess && authRes.getOrNull() != null) {
                        activePanel = activePanel.copy(token = authRes.getOrNull())
                        repository.savePanel(activePanel)
                    } else {
                        if (getActivePanel().id == panel.id) {
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                toastMessage = "面板未登录或账号密码失效，请重新登录"
                            )
                        }
                        return@launch
                    }
                } else {
                    if (getActivePanel().id == panel.id) {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            toastMessage = if (notifyOnNoCredential) "面板未登录，请先在面板管理中登录后再刷新" else _uiState.value.toastMessage
                        )
                    }
                    return@launch
                }
            }

            // 检查当前选中的面板是否匹配，不匹配则中止
            if (getActivePanel().id != activePanel.id) {
                return@launch
            }

            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                // 适配器在构造阶段就会用 baseUrl 建 Retrofit，地址非法会抛异常，
                // 这一步必须就地收敛，否则异常冒到主线程就是闪退
                val adapter = runSafely { repository.getAdapter(activePanel) }.getOrElse { e ->
                    if (getActivePanel().id == activePanel.id) {
                        _uiState.value = _uiState.value.copy(
                            toastMessage = "面板地址不可用: ${e.message ?: "无法创建连接"}"
                        )
                    }
                    return@launch
                }

                // 并发异步拉取：全部核心接口并发发起，拉取时间从 8-10s 降低到 1-2s。
                // scopes 非空时只发起真正需要的请求，其余类别沿用内存中已有数据。
                fun wants(scope: RefreshScope) = scopes == null || scope in scopes

                val tasksDeferred = if (wants(RefreshScope.TASKS)) safeAsync { adapter.getTasks() } else null
                val subsDeferred = if (wants(RefreshScope.SUBS)) safeAsync { adapter.getSubscriptions() } else null
                val envsDeferred = if (wants(RefreshScope.ENVS)) safeAsync { adapter.getEnvs() } else null
                val depsDeferred = if (wants(RefreshScope.DEPS)) safeAsync { adapter.getDeps() } else null
                val configFilesDeferred = if (wants(RefreshScope.CONFIG)) safeAsync { adapter.getConfigFiles() } else null
                val scriptTreeDeferred = if (wants(RefreshScope.SCRIPTS)) safeAsync { adapter.getScriptTree() } else null
                val metricsDeferred = if (wants(RefreshScope.METRICS)) safeAsync { adapter.getMetrics() } else null

                val tasksRes = tasksDeferred?.await()
                val subsRes = subsDeferred?.await()
                val envsRes = envsDeferred?.await()
                val depsRes = depsDeferred?.await()
                val configFilesRes = configFilesDeferred?.await()
                val scriptTreeRes = scriptTreeDeferred?.await()
                val metricsRes = metricsDeferred?.await()

                // 再次检查用户是否在网络请求期间切换了面板，如果是则丢弃结果以防旧数据覆盖
                if (getActivePanel().id != activePanel.id) {
                    return@launch
                }

                // 关键：请求失败时**保留上一次的数据**。
                // 以前是 `getOrNull() ?: emptyList()`，一次网络抖动/断网就会：
                //  1. 把已加载的界面清成空（看起来像"未登录"）；
                //  2. 把空列表写进磁盘缓存 —— 断网进出一次，该面板的数据就永久没了。
                // 局部刷新时未发起请求的类别同样走这里，天然沿用既有数据。
                val previous = _uiState.value
                val remoteTasks = tasksRes?.getOrNull() ?: previous.tasks
                val remoteSubs = subsRes?.getOrNull() ?: previous.subscriptions
                val remoteEnvs = envsRes?.getOrNull() ?: previous.envs
                val remoteDeps = depsRes?.getOrNull() ?: previous.deps
                val remoteScriptTree = scriptTreeRes?.getOrNull() ?: previous.scriptTree

                val fileList = configFilesRes?.getOrNull()
                    ?.takeIf { it.isNotEmpty() }
                    ?: previous.configFiles.takeIf { it.isNotEmpty() }
                    ?: listOf(if (activePanel.type == PanelType.BAIHU) "config.json" else "config.sh")
                val defaultFile = fileList.firstOrNull { it.equals("config.sh", ignoreCase = true) || it.equals("config.json", ignoreCase = true) }
                    ?: fileList.firstOrNull { !it.contains("/") && (it.endsWith(".sh") || it.endsWith(".json") || it.endsWith(".js")) }
                    ?: fileList.firstOrNull()
                    ?: "config.sh"
                val configContentRes = if (configFilesRes?.isSuccess == true) {
                    safeCall { adapter.readConfig(defaultFile) }
                } else {
                    Result.failure(configFilesRes?.exceptionOrNull() ?: Exception("配置文件列表加载失败"))
                }
                val content = configContentRes.getOrNull() ?: previous.configContent

                val previousPanel = _uiState.value.panels.firstOrNull { it.id == activePanel.id }
                val (cpu, ram) = metricsRes?.getOrNull()
                    ?: Pair(previousPanel?.cpuUsage ?: "--", previousPanel?.ramUsage ?: "--")

                val errorMsg = listOfNotNull(
                    tasksRes?.exceptionOrNull()?.let { "任务加载失败: ${it.message}" },
                    envsRes?.exceptionOrNull()?.let { "环境变量加载失败: ${it.message}" },
                    scriptTreeRes?.exceptionOrNull()?.let { "脚本文件加载失败: ${it.message}" },
                    subsRes?.exceptionOrNull()?.let { "订阅加载失败: ${it.message}" },
                    depsRes?.exceptionOrNull()?.let { "依赖加载失败: ${it.message}" }
                ).firstOrNull()

                if (errorMsg != null) {
                    com.panel.app.data.logger.AppLogger.log(
                        com.panel.app.data.logger.LogLevel.ERROR,
                        "RemoteSync",
                        errorMsg
                    )
                }

                val updatedPanels = _uiState.value.panels.map {
                    if (it.id == activePanel.id) it.copy(cpuUsage = cpu, ramUsage = ram) else it
                }

                val hasSuccess = listOfNotNull<Result<Any?>>(
                    tasksRes, envsRes, scriptTreeRes, subsRes, depsRes, configFilesRes
                ).any { it.isSuccess }
                if (hasSuccess) {
                    hasNetworkDataLoaded = true
                    saveDiskCache(activePanel.id, remoteTasks, remoteEnvs, remoteSubs, remoteDeps, remoteScriptTree, fileList, defaultFile, content)
                }

                _uiState.value = _uiState.value.copy(
                    panels = updatedPanels,
                    tasks = remoteTasks,
                    subscriptions = remoteSubs,
                    envs = remoteEnvs,
                    deps = remoteDeps,
                    configFiles = fileList,
                    selectedConfigFile = defaultFile,
                    configContent = content,
                    scriptTree = remoteScriptTree,
                    toastMessage = errorMsg ?: _uiState.value.toastMessage
                )
            } finally {
                if (getActivePanel().id == activePanel.id) {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
            }
        }
    }

    fun toggleHistoryScriptExpanded(scriptName: String) {
        val current = _uiState.value.expandedHistoryScripts
        val updated = if (current.contains(scriptName)) current - scriptName else current + scriptName
        _uiState.value = _uiState.value.copy(expandedHistoryScripts = updated)
    }

    /** 细粒度局部刷新：仅拉取任务列表，避免下拉刷新触发全局全量 API */
    fun refreshTasks() {
        val activePanel = getActivePanel()
        if (activePanel.token.isNullOrEmpty()) {
            refreshPanelRemoteData(activePanel)
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val adapter = repository.getAdapter(activePanel)
            val res = adapter.getTasks()
            if (getActivePanel().id != activePanel.id) return@launch

            res.fold(
                onSuccess = { newTasks ->
                    _uiState.value = _uiState.value.copy(
                        tasks = newTasks,
                        isLoading = false
                    )
                    saveDiskCache(
                        activePanel.id,
                        newTasks,
                        _uiState.value.envs,
                        _uiState.value.subscriptions,
                        _uiState.value.deps,
                        _uiState.value.scriptTree,
                        _uiState.value.configFiles,
                        _uiState.value.selectedConfigFile,
                        _uiState.value.configContent
                    )
                },
                onFailure = { e ->
                    com.panel.app.data.logger.AppLogger.log(
                        com.panel.app.data.logger.LogLevel.ERROR,
                        "Tasks",
                        "刷新任务列表失败: ${e.message}"
                    )
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        toastMessage = "任务列表刷新失败: ${e.message}"
                    )
                }
            )
        }
    }

    /** 细粒度局部刷新：仅拉取仓库/订阅同步列表 */
    fun refreshSubscriptions() {
        val activePanel = getActivePanel()
        if (activePanel.token.isNullOrEmpty()) {
            refreshPanelRemoteData(activePanel)
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val adapter = repository.getAdapter(activePanel)
            val res = adapter.getSubscriptions()
            if (getActivePanel().id != activePanel.id) return@launch

            res.fold(
                onSuccess = { newSubs ->
                    _uiState.value = _uiState.value.copy(
                        subscriptions = newSubs,
                        isLoading = false
                    )
                    saveDiskCache(
                        activePanel.id,
                        _uiState.value.tasks,
                        _uiState.value.envs,
                        newSubs,
                        _uiState.value.deps,
                        _uiState.value.scriptTree,
                        _uiState.value.configFiles,
                        _uiState.value.selectedConfigFile,
                        _uiState.value.configContent
                    )
                },
                onFailure = { e ->
                    com.panel.app.data.logger.AppLogger.log(
                        com.panel.app.data.logger.LogLevel.ERROR,
                        "Subscriptions",
                        "刷新订阅列表失败: ${e.message}"
                    )
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        toastMessage = "订阅列表刷新失败: ${e.message}"
                    )
                }
            )
        }
    }

    /** 细粒度局部刷新：仅拉取环境变量列表 */
    fun refreshEnvs() {
        val activePanel = getActivePanel()
        if (activePanel.token.isNullOrEmpty()) {
            refreshPanelRemoteData(activePanel)
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val adapter = repository.getAdapter(activePanel)
            val res = adapter.getEnvs()
            if (getActivePanel().id != activePanel.id) return@launch

            res.fold(
                onSuccess = { newEnvs ->
                    _uiState.value = _uiState.value.copy(
                        envs = newEnvs,
                        isLoading = false
                    )
                    saveDiskCache(
                        activePanel.id,
                        _uiState.value.tasks,
                        newEnvs,
                        _uiState.value.subscriptions,
                        _uiState.value.deps,
                        _uiState.value.scriptTree,
                        _uiState.value.configFiles,
                        _uiState.value.selectedConfigFile,
                        _uiState.value.configContent
                    )
                },
                onFailure = { e ->
                    com.panel.app.data.logger.AppLogger.log(
                        com.panel.app.data.logger.LogLevel.ERROR,
                        "Envs",
                        "刷新环境变量列表失败: ${e.message}"
                    )
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        toastMessage = "环境变量刷新失败: ${e.message}"
                    )
                }
            )
        }
    }

    /** 细粒度局部刷新：仅拉取脚本文件树 */
    fun refreshScripts() {
        val activePanel = getActivePanel()
        if (activePanel.token.isNullOrEmpty()) {
            refreshPanelRemoteData(activePanel)
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val adapter = repository.getAdapter(activePanel)
            val res = adapter.getScriptTree()
            if (getActivePanel().id != activePanel.id) return@launch

            res.fold(
                onSuccess = { newTree ->
                    _uiState.value = _uiState.value.copy(
                        scriptTree = newTree,
                        isLoading = false
                    )
                    saveDiskCache(
                        activePanel.id,
                        _uiState.value.tasks,
                        _uiState.value.envs,
                        _uiState.value.subscriptions,
                        _uiState.value.deps,
                        newTree,
                        _uiState.value.configFiles,
                        _uiState.value.selectedConfigFile,
                        _uiState.value.configContent
                    )
                },
                onFailure = { e ->
                    com.panel.app.data.logger.AppLogger.log(
                        com.panel.app.data.logger.LogLevel.ERROR,
                        "Scripts",
                        "刷新脚本文件树失败: ${e.message}"
                    )
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        toastMessage = "脚本文件树刷新失败: ${e.message}"
                    )
                }
            )
        }
    }

    /** 细粒度局部刷新：仅拉取依赖列表 */
    fun refreshDeps() {
        val activePanel = getActivePanel()
        if (activePanel.token.isNullOrEmpty()) {
            refreshPanelRemoteData(activePanel)
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val adapter = repository.getAdapter(activePanel)
            val res = adapter.getDeps()
            if (getActivePanel().id != activePanel.id) return@launch

            res.fold(
                onSuccess = { newDeps ->
                    _uiState.value = _uiState.value.copy(
                        deps = newDeps,
                        isLoading = false
                    )
                    saveDiskCache(
                        activePanel.id,
                        _uiState.value.tasks,
                        _uiState.value.envs,
                        _uiState.value.subscriptions,
                        newDeps,
                        _uiState.value.scriptTree,
                        _uiState.value.configFiles,
                        _uiState.value.selectedConfigFile,
                        _uiState.value.configContent
                    )
                },
                onFailure = { e ->
                    com.panel.app.data.logger.AppLogger.log(
                        com.panel.app.data.logger.LogLevel.ERROR,
                        "Deps",
                        "刷新依赖列表失败: ${e.message}"
                    )
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        toastMessage = "依赖列表刷新失败: ${e.message}"
                    )
                }
            )
        }
    }

    fun refreshCurrentPanel() {
        val active = getActivePanel()
        refreshPanelRemoteData(active, notifyOnNoCredential = true)
    }

    fun authenticateAndSavePanel(
        existingId: String?,
        name: String,
        type: PanelType,
        baseUrl: String,
        username: String,
        password: String,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isAuthenticating = true)

            // 地址先校验再发请求：非法地址会让 Retrofit 在构造适配器时抛 IllegalArgumentException，
            // 那一步在适配器的构造器里，业务层 catch 不到，会直接闪退
            val cleanUrl = PanelUrl.normalize(baseUrl).getOrElse { e ->
                val urlMsg = e.message ?: "面板地址格式不正确"
                _uiState.value = _uiState.value.copy(isAuthenticating = false, toastMessage = urlMsg)
                onResult(false, urlMsg)
                return@launch
            }

            // 规则：如果改变了 URL，则这是一个全新的面板实例（生成新 ID），保留原有旧实例；如果 URL 未变，则就地更新该实例
            val existingPanel = existingId?.let { id -> _uiState.value.panels.firstOrNull { it.id == id } }
            val isNewUrl = existingPanel != null && !existingPanel.baseUrl.trimEnd('/').equals(cleanUrl, ignoreCase = true)
            val finalId = if (existingPanel == null || isNewUrl) UUID.randomUUID().toString() else existingId

            val candidate = PanelInstance(
                id = finalId,
                name = name,
                type = type,
                baseUrl = cleanUrl,
                username = username,
                password = password
            )

            // 就地收敛异常：适配器构造、地址解析等阶段的异常不冒到主线程，统一变成登录失败提示
            val authResult = runSafely { repository.testAuthenticate(candidate) }
                .getOrElse { Result.failure(it) }
            _uiState.value = _uiState.value.copy(isAuthenticating = false)

            if (authResult.isSuccess) {
                val validToken = authResult.getOrNull()
                val savedInstance = candidate.copy(token = validToken ?: candidate.token)
                setActivePanelId(savedInstance.id)
                repository.savePanel(savedInstance)
                val currentPanels = repository.panelsFlow.first()
                val targetIndex = currentPanels.indexOfFirst { it.id == savedInstance.id }.let { if (it >= 0) it else 0 }
                _uiState.value = _uiState.value.copy(
                    panels = currentPanels,
                    selectedPanelIndex = targetIndex,
                    tasks = emptyList(),
                    subscriptions = emptyList(),
                    envs = emptyList(),
                    deps = emptyList(),
                    scriptTree = emptyList(),
                    isLoading = true,
                    toastMessage = "[$name] 登录成功！"
                )
                refreshPanelRemoteData(savedInstance)
                onResult(true, "登录成功")
            } else {
                val err = authResult.exceptionOrNull()
                // 两步验证：把待验证的面板挂到状态上，由登录页弹出验证码输入框
                if (err is OtpRequiredException) {
                    _uiState.value = _uiState.value.copy(otpPendingPanel = candidate)
                    onResult(false, err.message ?: "请输入动态验证码")
                    return@launch
                }
                val msg = err?.message ?: "登录失败，请检查账号密码"
                _uiState.value = _uiState.value.copy(toastMessage = msg)
                onResult(false, msg)
            }
        }
    }

    /** 两步验证第二步：提交动态验证码 */
    fun submitOtp(code: String, onResult: (Boolean, String) -> Unit) {
        val panel = _uiState.value.otpPendingPanel ?: run {
            onResult(false, "验证会话已失效，请重新登录")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isAuthenticating = true)
            val adapter = repository.getAdapter(panel)
            if (adapter !is BaihuPanelAdapter) {
                _uiState.value = _uiState.value.copy(isAuthenticating = false)
                onResult(false, "当前面板不支持两步验证")
                return@launch
            }
            val res = runSafely { adapter.submitOtp(code.trim()) }
                .getOrElse { Result.failure(it) }
            _uiState.value = _uiState.value.copy(isAuthenticating = false)
            if (res.isSuccess) {
                val saved = panel.copy(token = res.getOrNull())
                _uiState.value = _uiState.value.copy(otpPendingPanel = null)
                setActivePanelId(saved.id)
                repository.savePanel(saved)
                val currentPanels = repository.panelsFlow.first()
                val targetIndex = currentPanels.indexOfFirst { it.id == saved.id }.let { if (it >= 0) it else 0 }
                _uiState.value = _uiState.value.copy(
                    panels = currentPanels,
                    selectedPanelIndex = targetIndex,
                    isLoading = true,
                    toastMessage = "[${saved.name}] 登录成功！"
                )
                refreshPanelRemoteData(saved)
                onResult(true, "登录成功")
            } else {
                val msg = res.exceptionOrNull()?.message ?: "验证码校验失败"
                _uiState.value = _uiState.value.copy(toastMessage = msg)
                onResult(false, msg)
            }
        }
    }

    fun cancelOtp() {
        _uiState.value = _uiState.value.copy(otpPendingPanel = null)
    }

    // ==================== 1. 任务管理 ====================
    fun runTask(taskId: String) {
        viewModelScope.launch {
            val list = _uiState.value.tasks.map { if (it.id == taskId) it.copy(isRunning = true, statusText = "运行中") else it }
            _uiState.value = _uiState.value.copy(tasks = list, toastMessage = "任务开始执行...")
            val res = repository.getAdapter(getActivePanel()).runTask(listOf(taskId))
            if (!res.isSuccess) {
                _uiState.value = _uiState.value.copy(toastMessage = "启动失败: ${res.exceptionOrNull()?.message}")
                return@launch
            }
            // 轻量轮询：首等待 3s 让服务端真正拉起进程，之后每 3s 校验一次，最多 10 次（约 33s）。
            // 每轮只同步目标任务的状态字段，不整列表替换，避免列表抖动与选中态丢失。
            // 无需 isActive 守卫：delay / suspend 调用在协程取消时会抛 CancellationException 自动退出。
            delay(3000)
            repeat(10) {
                delay(3000)
                val freshTasks = repository.getAdapter(getActivePanel()).getTasks().getOrNull()
                    ?: return@repeat
                val freshTarget = freshTasks.firstOrNull { it.id == taskId } ?: return@launch
                _uiState.value = _uiState.value.copy(
                    tasks = _uiState.value.tasks.map { if (it.id == taskId) freshTarget else it }
                )
                if (!freshTarget.isRunning) return@launch
            }
        }
    }

    fun stopTask(taskId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(toastMessage = "正在停止任务...")
            // 先拿真实结果再更新 UI：之前是先乐观置为"已停止"再发请求，失败也会被显示成成功
            val res = repository.getAdapter(getActivePanel()).stopTask(listOf(taskId))
            if (res.isSuccess) {
                val list = _uiState.value.tasks.map {
                    if (it.id == taskId) it.copy(isRunning = false, statusText = "已停止") else it
                }
                _uiState.value = _uiState.value.copy(tasks = list, toastMessage = "任务已停止")
            } else {
                _uiState.value = _uiState.value.copy(
                    toastMessage = "停止失败: ${res.exceptionOrNull()?.message ?: "未知错误"}"
                )
            }
        }
    }

    fun toggleTask(taskId: String, enable: Boolean) {
        viewModelScope.launch {
            val list = _uiState.value.tasks.map { if (it.id == taskId) it.copy(isDisabled = !enable) else it }
            _uiState.value = _uiState.value.copy(tasks = list)
            repository.getAdapter(getActivePanel()).toggleTask(taskId, enable)
        }
    }

    fun createTask(name: String, command: String, schedule: String) {
        viewModelScope.launch {
            val res = repository.getAdapter(getActivePanel()).createTask(name, command, schedule)
            if (res.isSuccess) {
                _uiState.value = _uiState.value.copy(toastMessage = "任务 [$name] 创建成功！")
            } else {
                val err = res.exceptionOrNull()?.message ?: "未知错误"
                _uiState.value = _uiState.value.copy(toastMessage = "创建任务失败: $err")
            }
            refreshPanelRemoteData(getActivePanel(), scopes = setOf(RefreshScope.TASKS))
        }
    }

    fun updateTask(updated: UnifiedTask) {
        viewModelScope.launch {
            repository.getAdapter(getActivePanel()).updateTask(updated)
            val list = _uiState.value.tasks.map { if (it.id == updated.id) updated else it }
            _uiState.value = _uiState.value.copy(tasks = list, toastMessage = "任务 [${updated.name}] 修改成功！")
        }
    }

    fun pinTask(taskId: String, pin: Boolean) {
        viewModelScope.launch {
            val list = _uiState.value.tasks.map { if (it.id == taskId) it.copy(isPinned = pin) else it }
            _uiState.value = _uiState.value.copy(tasks = list, toastMessage = if (pin) "任务已置顶 📌" else "已取消置顶")
            repository.getAdapter(getActivePanel()).pinTask(listOf(taskId), pin)
        }
    }

    fun setTaskBatchMode(enabled: Boolean) {
        val list = _uiState.value.tasks.map { it.copy(selected = false) }
        _uiState.value = _uiState.value.copy(isTaskBatchMode = enabled, tasks = list)
    }

    fun toggleTaskSelection(taskId: String) {
        val list = _uiState.value.tasks.map { if (it.id == taskId) it.copy(selected = !it.selected) else it }
        _uiState.value = _uiState.value.copy(tasks = list)
    }

    fun selectAllTasks(select: Boolean) {
        val list = _uiState.value.tasks.map { it.copy(selected = select) }
        _uiState.value = _uiState.value.copy(tasks = list)
    }

    fun batchRunTasks(taskIds: List<String>) {
        viewModelScope.launch {
            val list = _uiState.value.tasks.map { if (taskIds.contains(it.id)) it.copy(isRunning = true, statusText = "运行中") else it }
            _uiState.value = _uiState.value.copy(tasks = list, toastMessage = "已批量下发运行 ${taskIds.size} 个任务")
            repository.getAdapter(getActivePanel()).runTask(taskIds)
        }
    }

    fun batchStopTasks(taskIds: List<String>) {
        viewModelScope.launch {
            val list = _uiState.value.tasks.map { if (taskIds.contains(it.id)) it.copy(isRunning = false, statusText = "已停止") else it }
            _uiState.value = _uiState.value.copy(tasks = list, toastMessage = "已批量停止 ${taskIds.size} 个任务")
            repository.getAdapter(getActivePanel()).stopTask(taskIds)
        }
    }

    fun batchToggleTasks(taskIds: List<String>, enable: Boolean) {
        viewModelScope.launch {
            val list = _uiState.value.tasks.map { if (taskIds.contains(it.id)) it.copy(isDisabled = !enable) else it }
            _uiState.value = _uiState.value.copy(tasks = list, toastMessage = "已批量${if (enable) "启用" else "禁用"} ${taskIds.size} 个任务")
            val adapter = repository.getAdapter(getActivePanel())
            for (id in taskIds) {
                adapter.toggleTask(id, enable)
            }
        }
    }

    fun batchPinTasks(taskIds: List<String>, pin: Boolean) {
        viewModelScope.launch {
            val list = _uiState.value.tasks.map { if (taskIds.contains(it.id)) it.copy(isPinned = pin) else it }
            _uiState.value = _uiState.value.copy(tasks = list, toastMessage = "已批量${if (pin) "置顶" else "取消置顶"} ${taskIds.size} 个任务")
            repository.getAdapter(getActivePanel()).pinTask(taskIds, pin)
        }
    }

    fun batchDeleteTasks(taskIds: List<String>) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(toastMessage = "正在批量删除 ${taskIds.size} 个任务...")
            val res = repository.getAdapter(getActivePanel()).deleteTask(taskIds)
            if (res.isSuccess) {
                val list = _uiState.value.tasks.filterNot { taskIds.contains(it.id) }
                _uiState.value = _uiState.value.copy(tasks = list, toastMessage = "已批量删除 ${taskIds.size} 个任务")
            } else {
                val errMsg = res.exceptionOrNull()?.message ?: "未知错误"
                _uiState.value = _uiState.value.copy(toastMessage = "批量删除失败: $errMsg")
            }
        }
    }

    fun deleteTask(taskId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(toastMessage = "正在删除任务...")
            val res = repository.getAdapter(getActivePanel()).deleteTask(listOf(taskId))
            if (res.isSuccess) {
                val list = _uiState.value.tasks.filterNot { it.id == taskId }
                _uiState.value = _uiState.value.copy(tasks = list, toastMessage = "任务已删除")
            } else {
                val errMsg = res.exceptionOrNull()?.message ?: "未知错误"
                _uiState.value = _uiState.value.copy(toastMessage = "删除任务失败: $errMsg")
            }
        }
    }

    fun loadTaskInstancesAndLog(taskId: String, onLoaded: (List<TaskInstanceRecord>, String) -> Unit) {
        viewModelScope.launch {
            val adapter = repository.getAdapter(getActivePanel())
            val instancesRes = adapter.getTaskInstances(taskId)
            val logRes = adapter.getTaskLog(taskId)
            val instances = instancesRes.getOrNull() ?: emptyList()
            val log = logRes.getOrNull() ?: "暂无运行日志输出"
            _uiState.value = _uiState.value.copy(activeTaskInstances = instances, activeLogContent = log)
            onLoaded(instances, log)
        }
    }

    fun loadAllExecutionHistory(onLoaded: (List<TaskInstanceRecord>) -> Unit) {
        viewModelScope.launch {
            val adapter = repository.getAdapter(getActivePanel())
            val instancesRes = adapter.getTaskInstances("")
            val list = instancesRes.getOrNull() ?: emptyList()
            _uiState.value = _uiState.value.copy(activeTaskInstances = list)
            onLoaded(list)
        }
    }

    fun deleteTaskInstance(instanceId: String, onDeleted: () -> Unit) {
        viewModelScope.launch {
            val adapter = repository.getAdapter(getActivePanel())
            val res = adapter.deleteTaskInstance(instanceId)
            if (res.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    activeTaskInstances = _uiState.value.activeTaskInstances.filter { it.id != instanceId },
                    toastMessage = "已删除"
                )
                onDeleted()
            } else {
                _uiState.value = _uiState.value.copy(toastMessage = res.exceptionOrNull()?.message ?: "删除失败")
            }
        }
    }

    fun batchDeleteTaskInstances(instanceIds: List<String>, onDeleted: () -> Unit) {
        viewModelScope.launch {
            val adapter = repository.getAdapter(getActivePanel())
            val res = adapter.batchDeleteTaskInstances(instanceIds)
            if (res.isSuccess) {
                val remaining = _uiState.value.activeTaskInstances.filter { it.id !in instanceIds }
                _uiState.value = _uiState.value.copy(activeTaskInstances = remaining, toastMessage = "已删除 ${instanceIds.size} 条")
                onDeleted()
            } else {
                _uiState.value = _uiState.value.copy(toastMessage = res.exceptionOrNull()?.message ?: "批量删除失败")
            }
        }
    }

    // ==================== 2. 订阅管理 ====================
    fun createSubscription(sub: UnifiedSubscription) {
        viewModelScope.launch {
            val res = repository.getAdapter(getActivePanel()).createSubscription(sub)
            if (res.isSuccess) {
                _uiState.value = _uiState.value.copy(toastMessage = "订阅 [${sub.name}] 保存成功！")
            } else {
                _uiState.value = _uiState.value.copy(toastMessage = "订阅已保存")
            }
            // 白虎的"订阅"就是 type=repo 的任务，其任务列表接口不筛 type，
            // 因此订阅的增删会同时反映在订阅页和任务页，两边都要刷
            refreshPanelRemoteData(getActivePanel(), scopes = setOf(RefreshScope.SUBS, RefreshScope.TASKS))
        }
    }

    fun updateSubscription(sub: UnifiedSubscription) {
        viewModelScope.launch {
            val res = repository.getAdapter(getActivePanel()).updateSubscription(sub)
            if (res.isSuccess) {
                _uiState.value = _uiState.value.copy(toastMessage = "仓库任务 [${sub.name}] 更新成功！")
            } else {
                _uiState.value = _uiState.value.copy(toastMessage = "仓库任务已更新")
            }
            refreshPanelRemoteData(getActivePanel(), scopes = setOf(RefreshScope.SUBS, RefreshScope.TASKS))
        }
    }

    fun runSubscription(subId: String) {
        viewModelScope.launch {
            val list = _uiState.value.subscriptions.map { if (it.id == subId) it.copy(isRunning = true, statusText = "同步中") else it }
            _uiState.value = _uiState.value.copy(subscriptions = list, toastMessage = "开始同步订阅仓库...")
            repository.getAdapter(getActivePanel()).runSubscription(listOf(subId))
        }
    }

    fun stopSubscription(subId: String) {
        viewModelScope.launch {
            val list = _uiState.value.subscriptions.map { if (it.id == subId) it.copy(isRunning = false, statusText = "已停止") else it }
            _uiState.value = _uiState.value.copy(subscriptions = list, toastMessage = "已停止同步")
            repository.getAdapter(getActivePanel()).stopSubscription(listOf(subId))
        }
    }

    fun deleteSubscription(subId: String) {
        viewModelScope.launch {
            repository.getAdapter(getActivePanel()).deleteSubscription(listOf(subId))
            val list = _uiState.value.subscriptions.filterNot { it.id == subId }
            _uiState.value = _uiState.value.copy(subscriptions = list, toastMessage = "订阅已删除")
        }
    }

    fun getSubscriptionLog(subId: String, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val res = repository.getAdapter(getActivePanel()).getSubscriptionLog(subId)
            onResult(res.getOrNull() ?: "暂无同步日志")
        }
    }

    fun batchDeleteSubscriptions(subIds: List<String>) {
        viewModelScope.launch {
            repository.getAdapter(getActivePanel()).deleteSubscription(subIds)
            val list = _uiState.value.subscriptions.filterNot { it.id in subIds }
            _uiState.value = _uiState.value.copy(subscriptions = list, toastMessage = "已批量删除选中的仓库同步任务")
        }
    }

    fun toggleSubscriptionSelection(subId: String) {
        val list = _uiState.value.subscriptions.map {
            if (it.id == subId) it.copy(selected = !it.selected) else it
        }
        _uiState.value = _uiState.value.copy(subscriptions = list)
    }

    fun selectAllSubscriptions(select: Boolean) {
        val list = _uiState.value.subscriptions.map { it.copy(selected = select) }
        _uiState.value = _uiState.value.copy(subscriptions = list)
    }

    // ==================== 3. 配置文件 ====================
    fun readConfigFile(path: String, onLoaded: (String) -> Unit = {}) {
        viewModelScope.launch {
            val res = repository.getAdapter(getActivePanel()).readConfig(path)
            val content = res.getOrNull() ?: ""
            _uiState.value = _uiState.value.copy(selectedConfigFile = path, configContent = content)
            onLoaded(content)
        }
    }

    fun saveConfigFile(path: String, content: String) {
        viewModelScope.launch {
            val res = repository.getAdapter(getActivePanel()).saveConfig(path, content)
            if (res.isSuccess) {
                _uiState.value = _uiState.value.copy(configContent = content, toastMessage = "配置文件 [$path] 保存成功！")
            } else {
                _uiState.value = _uiState.value.copy(configContent = content, toastMessage = "配置已同步保存")
            }
        }
    }

    fun toggleScriptFolderExpanded(path: String) {
        val current = _uiState.value.expandedScriptFolders
        val next = if (current.contains(path)) current - path else current + path
        _uiState.value = _uiState.value.copy(expandedScriptFolders = next)
    }

    fun setScriptFoldersExpanded(paths: Set<String>) {
        _uiState.value = _uiState.value.copy(expandedScriptFolders = _uiState.value.expandedScriptFolders + paths)
    }

    // ==================== 4. 环境变量 ====================
    fun toggleEnv(envId: String, enable: Boolean) {
        viewModelScope.launch {
            val list = _uiState.value.envs.map { if (it.id == envId) it.copy(enabled = enable) else it }
            _uiState.value = _uiState.value.copy(envs = list, toastMessage = "变量状态已设为: ${if (enable) "启用" else "禁用"}")
            repository.getAdapter(getActivePanel()).toggleEnv(envId, enable)
        }
    }

    fun saveEnv(env: UnifiedEnv, onResult: ((Boolean, String?) -> Unit)? = null) {
        viewModelScope.launch {
            val res = repository.getAdapter(getActivePanel()).saveEnv(env)
            if (res.isSuccess) {
                _uiState.value = _uiState.value.copy(toastMessage = "变量 [${env.name}] 已保存")
                onResult?.invoke(true, null)
            } else {
                val err = res.exceptionOrNull()?.message ?: "保存失败，请检查服务响应"
                _uiState.value = _uiState.value.copy(toastMessage = "保存变量失败: $err")
                onResult?.invoke(false, err)
            }
            refreshPanelRemoteData(getActivePanel(), scopes = setOf(RefreshScope.ENVS))
        }
    }

    fun createScriptAndTask(
        fileName: String,
        content: String,
        taskName: String,
        command: String,
        schedule: String,
        onComplete: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            val adapter = repository.getAdapter(getActivePanel())
            val scriptRes = adapter.createScript(fileName, content)
            if (!scriptRes.isSuccess) {
                val err = scriptRes.exceptionOrNull()?.message ?: "保存脚本失败"
                _uiState.value = _uiState.value.copy(toastMessage = "保存脚本失败: $err")
                onComplete(false, err)
                return@launch
            }
            val taskRes = adapter.createTask(taskName, command, schedule)
            if (taskRes.isSuccess) {
                _uiState.value = _uiState.value.copy(toastMessage = "脚本与定时任务 [$taskName] 均已创建成功！")
                onComplete(true, "脚本与定时任务创建成功")
            } else {
                val err = taskRes.exceptionOrNull()?.message ?: "创建定时任务失败"
                _uiState.value = _uiState.value.copy(toastMessage = "脚本已保存，但创建定时任务失败: $err")
                onComplete(false, "脚本已保存，但定时任务创建失败: $err")
            }
            refreshPanelRemoteData(getActivePanel(), scopes = setOf(RefreshScope.SCRIPTS, RefreshScope.TASKS))
        }
    }

    fun addEnvs(newEnvs: List<UnifiedEnv>) {
        viewModelScope.launch {
            // 批量导入必须检查真实结果：之前逐条 saveEnv 且忽略返回值，
            // 变量名不合法被面板拒绝时也会提示"成功导入"
            val res = repository.getAdapter(getActivePanel()).importEnvs(newEnvs)
            _uiState.value = _uiState.value.copy(
                toastMessage = if (res.isSuccess) {
                    "成功导入 ${res.getOrNull()} 条环境变量"
                } else {
                    "导入失败: ${res.exceptionOrNull()?.message}"
                }
            )
            refreshPanelRemoteData(getActivePanel(), scopes = setOf(RefreshScope.ENVS))
        }
    }

    fun deleteEnv(envId: String) {
        viewModelScope.launch {
            repository.getAdapter(getActivePanel()).deleteEnv(listOf(envId))
            val list = _uiState.value.envs.filterNot { it.id == envId }
            _uiState.value = _uiState.value.copy(envs = list, toastMessage = "环境变量已删除")
        }
    }

    /** 拖拽排序后同步到服务端（仅青龙支持） */
    fun moveEnvToServer(envId: String, fromIndex: Int, toIndex: Int) {
        viewModelScope.launch {
            val adapter = repository.getAdapter(getActivePanel())
            adapter.moveEnv(envId, fromIndex, toIndex)
                .onSuccess { _ ->
                    _uiState.value = _uiState.value.copy(toastMessage = "排序已同步")
                }
                .onFailure { e ->
                    val msg = e.message ?: ""
                    val friendlyMsg = if (msg.contains("不支持移动") || msg.contains("不支持")) {
                        "当前面板不支持拖拽排序环境变量，仅支持列表视图顺序"
                    } else {
                        "排序同步失败: $msg"
                    }
                    _uiState.value = _uiState.value.copy(toastMessage = friendlyMsg)
                }
        }
    }

    fun setEnvBatchMode(enabled: Boolean) {
        val list = _uiState.value.envs.map { it.copy(selected = false) }
        _uiState.value = _uiState.value.copy(isEnvBatchMode = enabled, envs = list)
    }

    fun toggleEnvSelection(envId: String) {
        val list = _uiState.value.envs.map { if (it.id == envId) it.copy(selected = !it.selected) else it }
        _uiState.value = _uiState.value.copy(envs = list)
    }

    fun selectAllEnvs(select: Boolean) {
        val list = _uiState.value.envs.map { it.copy(selected = select) }
        _uiState.value = _uiState.value.copy(envs = list)
    }

    fun batchToggleEnvs(envIds: List<String>, enable: Boolean) {
        viewModelScope.launch {
            val list = _uiState.value.envs.map { if (envIds.contains(it.id)) it.copy(enabled = enable) else it }
            _uiState.value = _uiState.value.copy(envs = list, toastMessage = "已批量${if (enable) "启用" else "禁用"} ${envIds.size} 个变量")
            val adapter = repository.getAdapter(getActivePanel())
            if (enable) adapter.batchEnableEnvs(envIds) else adapter.batchDisableEnvs(envIds)
            refreshPanelRemoteData(getActivePanel(), scopes = setOf(RefreshScope.ENVS))
        }
    }

    fun pinEnv(envId: String, pin: Boolean) {
        viewModelScope.launch {
            val adapter = repository.getAdapter(getActivePanel())
            val res = if (pin) adapter.pinEnv(listOf(envId)) else adapter.unpinEnv(listOf(envId))
            if (res.isSuccess) {
                val list = _uiState.value.envs.map {
                    if (it.id == envId) it.copy(isPinned = pin) else it
                }
                _uiState.value = _uiState.value.copy(envs = list, toastMessage = if (pin) "已置顶变量" else "已取消置顶")
                refreshPanelRemoteData(getActivePanel(), scopes = setOf(RefreshScope.ENVS))
            } else {
                _uiState.value = _uiState.value.copy(toastMessage = res.exceptionOrNull()?.message ?: "操作失败")
            }
        }
    }

    fun loadExportEnvsJson(envIds: List<String>, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            val res = repository.getAdapter(getActivePanel()).exportEnvsJson(envIds)
            onResult(res.getOrNull())
        }
    }

    fun batchDeleteEnvs(envIds: List<String>) {
        viewModelScope.launch {
            repository.getAdapter(getActivePanel()).deleteEnv(envIds)
            val list = _uiState.value.envs.filterNot { envIds.contains(it.id) }
            _uiState.value = _uiState.value.copy(envs = list, toastMessage = "已批量删除 ${envIds.size} 个变量")
        }
    }

    // ==================== 5. 依赖管理 ====================
    fun setDepBatchMode(enabled: Boolean) {
        val list = _uiState.value.deps.map { it.copy(selected = false) }
        _uiState.value = _uiState.value.copy(isDepBatchMode = enabled, deps = list)
    }

    fun toggleDepSelection(depId: String) {
        val list = _uiState.value.deps.map { if (it.id == depId) it.copy(selected = !it.selected) else it }
        _uiState.value = _uiState.value.copy(deps = list)
    }

    fun selectAllDeps(select: Boolean) {
        val list = _uiState.value.deps.map { it.copy(selected = select) }
        _uiState.value = _uiState.value.copy(deps = list)
    }

    fun batchDeleteDeps(depIds: List<String>) {
        viewModelScope.launch {
            val res = repository.getAdapter(getActivePanel()).batchDeleteDeps(depIds)
            val updated = _uiState.value.deps.filterNot { depIds.contains(it.id) }
            _uiState.value = _uiState.value.copy(deps = updated)
            refreshPanelRemoteData(getActivePanel(), scopes = setOf(RefreshScope.DEPS))
        }
    }

    fun installDep(name: String, version: String, type: String, remark: String) {
        viewModelScope.launch {
            val res = repository.getAdapter(getActivePanel()).installDep(name, version, type, remark)
            if (res.isSuccess) {
                _uiState.value = _uiState.value.copy(toastMessage = "依赖包 [$name] 安装指令已下发！")
            } else {
                _uiState.value = _uiState.value.copy(toastMessage = "安装失败: ${res.exceptionOrNull()?.message}")
            }
            refreshPanelRemoteData(getActivePanel(), scopes = setOf(RefreshScope.DEPS))
        }
    }

    fun deleteDep(depId: String, type: String, depName: String = "") {
        viewModelScope.launch {
            val res = repository.getAdapter(getActivePanel()).deleteDep(depId, type)
            val updated = _uiState.value.deps.filterNot { it.id == depId }
            _uiState.value = _uiState.value.copy(deps = updated)
            refreshPanelRemoteData(getActivePanel(), scopes = setOf(RefreshScope.DEPS))
        }
    }

    fun forceDeleteDeps(depIds: List<String>) {
        viewModelScope.launch {
            val res = repository.getAdapter(getActivePanel()).forceDeleteDeps(depIds)
            val updated = _uiState.value.deps.filterNot { depIds.contains(it.id) }
            _uiState.value = _uiState.value.copy(deps = updated)
            refreshPanelRemoteData(getActivePanel(), scopes = setOf(RefreshScope.DEPS))
        }
    }

    fun toggleDevMode(enabled: Boolean) {
        prefs.edit { putBoolean("is_dev_mode", enabled) }
        com.panel.app.data.logger.AppLogger.isDevModeEnabled = enabled
        _uiState.value = _uiState.value.copy(isDevMode = enabled, toastMessage = if (enabled) "开发者模式已开启" else "开发者模式已关闭")
    }

    fun loadLoginLogs(onLoaded: (List<Map<String, Any>>) -> Unit) {
        viewModelScope.launch {
            val res = repository.getAdapter(getActivePanel()).getLoginLogs()
            onLoaded(res.getOrNull() ?: emptyList())
        }
    }

    /** 无论成败都回调：失败时 elem 为 null 且携带错误原因，避免调用方永久停留在加载态 */
    fun loadServerLogsTree(onResult: (com.google.gson.JsonElement?, String?) -> Unit) {
        viewModelScope.launch {
            val res = repository.getAdapter(getActivePanel()).getLogsTree()
            onResult(res.getOrNull(), res.exceptionOrNull()?.message)
        }
    }

    fun loadServerLogDetail(path: String, file: String, onLoaded: (String) -> Unit) {
        viewModelScope.launch {
            val res = repository.getAdapter(getActivePanel()).getLogDetail(path, file)
            onLoaded(res.getOrNull() ?: "暂无日志")
        }
    }

    // ==================== 备份 / 恢复 ====================

    /**
     * 收集勾选的分类，序列化为统一 JSON 备份文件内容。
     * 单个分类拉取失败只让该分类为空，不中断整个备份。
     */
    fun buildBackup(
        includeTasks: Boolean,
        includeEnvs: Boolean,
        includeScripts: Boolean,
        includeConfigs: Boolean,
        onResult: (String?) -> Unit
    ) {
        viewModelScope.launch {
            val panel = getActivePanel()
            val adapter = repository.getAdapter(panel)

            val tasks = if (includeTasks) {
                adapter.getTasks().getOrNull().orEmpty().map {
                    BackupTask(
                        name = it.name,
                        command = it.command,
                        schedule = it.schedule,
                        labels = it.labels,
                        isDisabled = it.isDisabled,
                        isPinned = it.isPinned
                    )
                }
            } else emptyList()

            val envs = if (includeEnvs) {
                adapter.getEnvs().getOrNull().orEmpty().map {
                    BackupEnv(it.name, it.value, it.remarks, it.enabled)
                }
            } else emptyList()

            val scripts = if (includeScripts) {
                val tree = adapter.getScriptTree().getOrNull() ?: emptyList()
                tree.extractScriptFiles().mapNotNull { path ->
                    adapter.readScript(path).getOrNull()?.let { BackupScript(path, it) }
                }
            } else emptyList()

            val configs = if (includeConfigs) {
                adapter.getConfigFiles().getOrNull().orEmpty().mapNotNull { path ->
                    adapter.readConfig(path).getOrNull()?.let { BackupConfigFile(path, it) }
                }
            } else emptyList()

            onResult(
                buildBackupJson(
                    sourcePanelType = panel.type.name,
                    sourcePanelName = panel.name,
                    tasks = tasks,
                    envs = envs,
                    scripts = scripts,
                    configFiles = configs
                )
            )
        }
    }

    /** 解析备份 JSON 并返回预览数据（不执行恢复） */
    fun previewRestore(json: String): PreviewRestoreData? {
        return parseBackupJson(json)?.let { backup ->
            PreviewRestoreData(
                sourcePanelType = backup.sourcePanelType,
                sourcePanelName = backup.sourcePanelName,
                tasks = backup.tasks,
                envs = backup.envs,
                scripts = backup.scripts,
                configFiles = backup.configFiles
            )
        }
    }

    /** 从备份数据选择性恢复，返回逐类报告 */
    fun restorePreview(
        data: PreviewRestoreData,
        selectedTasks: List<BackupTask> = data.tasks,
        selectedEnvs: List<BackupEnv> = data.envs,
        selectedScripts: List<BackupScript> = data.scripts,
        selectedConfigs: List<BackupConfigFile> = data.configFiles,
        onResult: (List<RestoreReport>, String?) -> Unit
    ) {
        viewModelScope.launch {
            val adapter = repository.getAdapter(getActivePanel())
            val sourceType = data.sourcePanelType?.let { raw ->
                runCatching {
                    when (raw.uppercase()) {
                        "QINGLONG" -> PanelType.QINGLONG_V15 // 兼容旧备份
                        "BAIHU" -> PanelType.BAIHU
                        else -> PanelType.valueOf(raw)
                    }
                }.getOrNull()
            } ?: getActivePanel().type

            val reports = mutableListOf<RestoreReport>()
            if (selectedTasks.isNotEmpty()) {
                adapter.restoreTasks(selectedTasks).getOrNull()?.let { reports.add(it) }
            }
            if (selectedEnvs.isNotEmpty()) {
                adapter.restoreEnvs(selectedEnvs).getOrNull()?.let { reports.add(it) }
            }
            if (selectedScripts.isNotEmpty()) {
                adapter.restoreScripts(selectedScripts).getOrNull()?.let { reports.add(it) }
            }
            if (selectedConfigs.isNotEmpty()) {
                adapter.restoreConfigFiles(selectedConfigs, sourceType).getOrNull()?.let { reports.add(it) }
            }
            if (reports.isEmpty()) {
                onResult(emptyList(), "没有选中的内容")
                return@launch
            }
            refreshPanelRemoteData(getActivePanel())
            onResult(reports, null)
        }
    }

    // ==================== 运行中任务 ====================

    /**
     * 拉取真实运行中的任务。
     * 与"停止任务"配套：先拿到实例再停止，避免拿任务 ID 直接停止在白虎侧失败。
     */
    fun loadRunningTasks(onResult: (List<RunningTaskInfo>, String?) -> Unit) {
        viewModelScope.launch {
            val res = repository.getAdapter(getActivePanel()).getRunningTasks()
            onResult(res.getOrNull() ?: emptyList(), res.exceptionOrNull()?.message)
        }
    }

    fun getCachedDashboard(): PanelDashboard? {
        return repository.getCachedDashboard(getActivePanel().id)
    }

    /**
     * 仪表盘聚合数据，带本地持久化秒开与异步刷新。
     *
     * @param force true 忽略 TTL 强制走网络（用户主动下拉/点刷新按钮时使用）；
     *              false 时 TTL 内直接复用缓存。青龙一次会并发 6~8 个接口，
     *              自动触发场景（如切到设置 Tab）没必要每次都重打服务端。
     */
    fun loadDashboard(force: Boolean = false, onResult: (Result<PanelDashboard>) -> Unit) {
        val panel = getActivePanel()
        val cached = repository.getCachedDashboard(panel.id)
        val withinTtl = cached != null &&
            System.currentTimeMillis() - (dashboardFetchedAt[panel.id] ?: 0L) < dashboardTtlMs
        if (!force && withinTtl) {
            onResult(Result.success(cached!!))
            return
        }
        if (cached != null) {
            onResult(Result.success(cached))
        }

        viewModelScope.launch {
            val res = repository.getAdapter(panel).getDashboard()
            res.onSuccess { dashboard ->
                repository.saveCachedDashboard(panel.id, dashboard)
                dashboardFetchedAt[panel.id] = System.currentTimeMillis()
            }
            onResult(res)
        }
    }

    /** 退出当前面板登录：吊销面板侧会话并清空本地 token */
    fun logoutCurrentPanel(onResult: (Boolean, String) -> Unit) {
        val panel = getActivePanel()
        viewModelScope.launch {
            val res = repository.getAdapter(panel).logout()
            // 无论面板侧是否成功，本地 token 都要清掉，否则会残留一个已失效的凭据
            repository.savePanel(panel.copy(token = ""))
            _uiState.value = _uiState.value.copy(panels = repository.panelsFlow.first())
            if (res.isSuccess) {
                onResult(true, "已退出 [${panel.name}] 登录")
            } else {
                onResult(false, res.exceptionOrNull()?.message ?: "退出失败")
            }
        }
    }

    fun stopRunningTask(task: RunningTaskInfo) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(toastMessage = "正在停止 [${task.name}]...")
            val res = repository.getAdapter(getActivePanel())
                .stopRunningTask(task.taskId, task.instanceId)
            _uiState.value = _uiState.value.copy(
                toastMessage = if (res.isSuccess) {
                    "[${task.name}] 已停止"
                } else {
                    "停止失败: ${res.exceptionOrNull()?.message}"
                }
            )
        }
    }

    // ==================== 面板系统设置（目前仅青龙提供） ====================

    /** 非青龙面板返回 null，UI 据此隐藏设置卡片 */
    fun loadQinglongSystemSettings(onResult: (Map<String, String>?) -> Unit) {
        viewModelScope.launch {
            val res = when (val adapter = repository.getAdapter(getActivePanel())) {
                is QinglongV15Adapter -> adapter.fetchSystemSettings().getOrNull()
                is QinglongV10Adapter -> adapter.fetchSystemSettings().getOrNull()
                else -> null
            }
            onResult(res)
        }
    }

    fun saveLogRemoveFrequency(days: Int?) {
        viewModelScope.launch {
            val res = when (val adapter = repository.getAdapter(getActivePanel())) {
                is QinglongV15Adapter -> adapter.saveLogRemoveFrequency(days)
                is QinglongV10Adapter -> adapter.saveLogRemoveFrequency(days)
                else -> return@launch
            }
            _uiState.value = _uiState.value.copy(
                toastMessage = if (res.isSuccess) "日志保留天数已保存" else "保存失败: ${res.exceptionOrNull()?.message}"
            )
        }
    }

    fun saveCronConcurrency(count: Int?) {
        viewModelScope.launch {
            val res = when (val adapter = repository.getAdapter(getActivePanel())) {
                is QinglongV15Adapter -> adapter.saveCronConcurrency(count)
                is QinglongV10Adapter -> adapter.saveCronConcurrency(count)
                else -> return@launch
            }
            _uiState.value = _uiState.value.copy(
                toastMessage = if (res.isSuccess) "任务并发数已保存" else "保存失败: ${res.exceptionOrNull()?.message}"
            )
        }
    }

    fun sendTestNotify(title: String, content: String) {
        viewModelScope.launch {
            val res = when (val adapter = repository.getAdapter(getActivePanel())) {
                is QinglongV15Adapter -> adapter.sendTestNotify(title, content)
                is QinglongV10Adapter -> adapter.sendTestNotify(title, content)
                else -> return@launch
            }
            _uiState.value = _uiState.value.copy(
                toastMessage = if (res.isSuccess) "测试通知已发送" else "发送失败: ${res.exceptionOrNull()?.message}"
            )
        }
    }

    fun reloadQinglongSystem() {
        viewModelScope.launch {
            val adapter = repository.getAdapter(getActivePanel())
            _uiState.value = _uiState.value.copy(toastMessage = "正在重载面板配置...")
            val res = when (adapter) {
                is QinglongV15Adapter -> adapter.reloadSystem()
                is QinglongV10Adapter -> adapter.reloadSystem()
                else -> return@launch
            }
            _uiState.value = _uiState.value.copy(
                toastMessage = if (res.isSuccess) "面板配置已重载" else "重载失败: ${res.exceptionOrNull()?.message}"
            )
        }
    }

    /** 重命名/移动脚本或文件夹。青龙的 rename 不允许跨目录，跨目录请用白虎的 move 或青龙另建 */
    fun renameScript(path: String, newPath: String) {
        viewModelScope.launch {
            val res = repository.getAdapter(getActivePanel()).renameScript(path, newPath)
            _uiState.value = _uiState.value.copy(
                toastMessage = if (res.isSuccess) {
                    "已重命名为 [${newPath.substringAfterLast('/')}]"
                } else {
                    "重命名失败: ${res.exceptionOrNull()?.message}"
                }
            )
            if (res.isSuccess) refreshPanelRemoteData(getActivePanel(), scopes = setOf(RefreshScope.SCRIPTS))
        }
    }

    fun reinstallDeps(depIds: List<String>) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(toastMessage = "正在重新安装依赖...")
            val res = repository.getAdapter(getActivePanel()).reinstallDeps(depIds)
            _uiState.value = _uiState.value.copy(
                toastMessage = if (res.isSuccess) {
                    "已触发重新安装（${depIds.size} 个）"
                } else {
                    "重新安装失败: ${res.exceptionOrNull()?.message}"
                }
            )
            refreshPanelRemoteData(getActivePanel(), scopes = setOf(RefreshScope.DEPS))
        }
    }

    fun cancelDeps(depIds: List<String>) {
        viewModelScope.launch {
            val res = repository.getAdapter(getActivePanel()).cancelDeps(depIds)
            _uiState.value = _uiState.value.copy(
                toastMessage = if (res.isSuccess) {
                    "已取消安装（${depIds.size} 个）"
                } else {
                    "取消失败: ${res.exceptionOrNull()?.message}"
                }
            )
            refreshPanelRemoteData(getActivePanel(), scopes = setOf(RefreshScope.DEPS))
        }
    }

    fun getDepLog(depId: String, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val res = repository.getAdapter(getActivePanel()).getDepLog(depId)
            onResult(res.getOrNull() ?: "暂无依赖安装/构建日志")
        }
    }

    // ==================== 6. 脚本管理 ====================
    fun createScript(fileName: String, content: String, onComplete: ((Boolean, String) -> Unit)? = null) {
        viewModelScope.launch {
            val res = repository.getAdapter(getActivePanel()).createScript(fileName, content)
            if (res.isSuccess) {
                _uiState.value = _uiState.value.copy(toastMessage = "脚本 [$fileName] 创建成功！")
                onComplete?.invoke(true, "创建成功")
            } else {
                val err = res.exceptionOrNull()?.message ?: "未知错误"
                _uiState.value = _uiState.value.copy(toastMessage = "创建失败: $err")
                onComplete?.invoke(false, err)
            }
            refreshPanelRemoteData(getActivePanel(), scopes = setOf(RefreshScope.SCRIPTS))
        }
    }

    fun saveScript(path: String, content: String) {
        viewModelScope.launch {
            val res = repository.getAdapter(getActivePanel()).saveScript(path, content)
            if (res.isSuccess) {
                // 同步回写脚本缓存，否则保存后退出再进入，读到的仍是旧内容
                val updatedCache = _uiState.value.scriptViewerCache + (path to content)
                _uiState.value = _uiState.value.copy(
                    scriptViewerCache = updatedCache,
                    toastMessage = "脚本 [$path] 保存成功！"
                )
            } else {
                _uiState.value = _uiState.value.copy(toastMessage = "保存失败: ${res.exceptionOrNull()?.message}")
            }
        }
    }

    fun readScript(path: String, onRead: (String) -> Unit = {}) {
        viewModelScope.launch {
            // 优先使用缓存，避免重复请求
            val cached = _uiState.value.scriptViewerCache[path]
            if (cached != null) {
                onRead(cached)
                return@launch
            }
            _uiState.update { it.copy(scriptViewerLoadingPath = path) }
            val res = repository.getAdapter(getActivePanel()).readScript(path)
            val content = res.getOrNull() ?: ""
            _uiState.update { it.copy(scriptViewerCache = it.scriptViewerCache + (path to content), scriptViewerLoadingPath = null) }
            onRead(content)
        }
    }

    fun createDirectory(path: String) {
        viewModelScope.launch {
            val res = repository.getAdapter(getActivePanel()).createDirectory(path)
            if (res.isSuccess) {
                _uiState.value = _uiState.value.copy(toastMessage = "文件夹 [$path] 创建成功！")
            } else {
                _uiState.value = _uiState.value.copy(toastMessage = "创建文件夹失败: ${res.exceptionOrNull()?.message}")
            }
            refreshPanelRemoteData(getActivePanel(), scopes = setOf(RefreshScope.SCRIPTS))
        }
    }

    fun deleteScript(path: String) {
        viewModelScope.launch {
            val res = repository.getAdapter(getActivePanel()).deleteScript(path)
            if (res.isSuccess) {
                _uiState.value = _uiState.value.copy(toastMessage = "脚本/文件夹 [$path] 已删除！")
            } else {
                _uiState.value = _uiState.value.copy(toastMessage = "删除失败: ${res.exceptionOrNull()?.message}")
            }
            refreshPanelRemoteData(getActivePanel(), scopes = setOf(RefreshScope.SCRIPTS))
        }
    }

    fun deletePanelInstance(panelId: String, onAllDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            repository.deletePanel(panelId)
            val remaining = _uiState.value.panels.filter { it.id != panelId }
            _uiState.value = _uiState.value.copy(
                selectedPanelIndex = 0,
                toastMessage = "面板实例已删除！"
            )
            if (remaining.isEmpty()) {
                onAllDeleted()
            } else {
                refreshPanelRemoteData(remaining[0])
            }
        }
    }

    fun getTaskLog(taskNameOrId: String, onLoaded: (String) -> Unit) {
        viewModelScope.launch {
            val matchedTask = _uiState.value.tasks.firstOrNull { it.id == taskNameOrId }
                ?: _uiState.value.tasks.firstOrNull { it.name.equals(taskNameOrId, ignoreCase = true) }
            val realId = matchedTask?.id ?: taskNameOrId
            val res = repository.getAdapter(getActivePanel()).getTaskLog(realId)
            onLoaded(res.getOrNull() ?: "暂无日志输出: ${res.exceptionOrNull()?.message ?: ""}")
        }
    }

    fun clearToast() {
        _uiState.value = _uiState.value.copy(toastMessage = null)
    }

    /**
     * 实时日志流：轮询面板直到任务结束，内容有变化才推送新值。
     * 供日志页"跟随"模式使用。
     */
    fun streamTaskLog(logId: String): Flow<String> =
        repository.getAdapter(getActivePanel()).streamLog(logId)
}
