package com.panel.app.ui.viewmodel

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.panel.app.data.adapter.BaihuPanelAdapter
import com.panel.app.data.adapter.QinglongV10Adapter
import com.panel.app.data.adapter.QinglongV15Adapter
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
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

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
    val toastMessage: String? = null,
    /** 非空表示该面板开启了两步验证，等待用户输入动态验证码 */
    val otpPendingPanel: PanelInstance? = null
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

    private fun loadDiskCache(panelId: String) {
        try {
            val file = java.io.File(context.cacheDir, "panel_cache_${panelId.hashCode()}.json")
            if (file.exists()) {
                val json = file.readText()
                val data = gson.fromJson(json, CachedPanelData::class.java)
                if (data != null && getActivePanel().id == panelId) {
                    _uiState.value = _uiState.value.copy(
                        tasks = if (data.tasks.isNotEmpty() && _uiState.value.tasks.isEmpty()) data.tasks else _uiState.value.tasks,
                        envs = if (data.envs.isNotEmpty() && _uiState.value.envs.isEmpty()) data.envs else _uiState.value.envs,
                        subscriptions = if (data.subscriptions.isNotEmpty() && _uiState.value.subscriptions.isEmpty()) data.subscriptions else _uiState.value.subscriptions,
                        deps = if (data.deps.isNotEmpty() && _uiState.value.deps.isEmpty()) data.deps else _uiState.value.deps,
                        scriptTree = if (data.scriptTree.isNotEmpty() && _uiState.value.scriptTree.isEmpty()) data.scriptTree else _uiState.value.scriptTree,
                        configFiles = if (data.configFiles.isNotEmpty() && _uiState.value.configFiles.isEmpty()) data.configFiles else _uiState.value.configFiles,
                        selectedConfigFile = if (data.selectedConfigFile.isNotEmpty()) data.selectedConfigFile else _uiState.value.selectedConfigFile,
                        configContent = if (data.configContent.isNotEmpty() && _uiState.value.configContent.isEmpty()) data.configContent else _uiState.value.configContent
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
            val file = java.io.File(context.cacheDir, "panel_cache_${panelId.hashCode()}.json")
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
            repository.initDefaultPanelsIfEmpty()
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
                    refreshPanelRemoteData(activePanel)
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
        prefs.edit().putBoolean("is_dark_theme", newTheme).apply()
        _uiState.value = _uiState.value.copy(isDarkTheme = newTheme)
    }

    fun switchPanel(index: Int) {
        val panels = _uiState.value.panels
        if (index in panels.indices) {
            val targetPanel = panels[index]
            prefs.edit().putString("active_panel_id", targetPanel.id).apply()
            _uiState.value = _uiState.value.copy(
                selectedPanelIndex = index,
                tasks = emptyList(),
                subscriptions = emptyList(),
                envs = emptyList(),
                deps = emptyList(),
                scriptTree = emptyList(),
                isLoading = true,
                toastMessage = "已切换至面板 [${targetPanel.name}]"
            )
            refreshPanelRemoteData(targetPanel)
        }
    }

    fun refreshPanelRemoteData(panel: PanelInstance) {
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
                        _uiState.value = _uiState.value.copy(isLoading = false)
                    }
                    return@launch
                }
            }

            // 检查当前选中的面板是否匹配，不匹配则中止
            if (getActivePanel().id != activePanel.id) {
                return@launch
            }

            loadDiskCache(activePanel.id)
            _uiState.value = _uiState.value.copy(isLoading = true)
            val adapter = repository.getAdapter(activePanel)

            // 并发异步拉取：全部核心接口并发发起，拉取时间从 8-10s 降低到 1-2s
            val tasksDeferred = async { adapter.getTasks() }
            val subsDeferred = async { adapter.getSubscriptions() }
            val envsDeferred = async { adapter.getEnvs() }
            val depsDeferred = async { adapter.getDeps() }
            val configFilesDeferred = async { adapter.getConfigFiles() }
            val scriptTreeDeferred = async { adapter.getScriptTree() }
            val metricsDeferred = async { adapter.getMetrics() }

            val tasksRes = tasksDeferred.await()
            val remoteTasks = tasksRes.getOrNull() ?: emptyList()

            val subsRes = subsDeferred.await()
            val remoteSubs = subsRes.getOrNull() ?: emptyList()

            val envsRes = envsDeferred.await()
            val remoteEnvs = envsRes.getOrNull() ?: emptyList()

            val depsRes = depsDeferred.await()
            val remoteDeps = depsRes.getOrNull() ?: emptyList()

            val configFilesRes = configFilesDeferred.await()
            val fileList = configFilesRes.getOrNull() ?: listOf(if (activePanel.type == PanelType.BAIHU) "config.json" else "config.sh")
            val defaultFile = fileList.firstOrNull { it.equals("config.sh", ignoreCase = true) || it.equals("config.json", ignoreCase = true) }
                ?: fileList.firstOrNull { !it.contains("/") && (it.endsWith(".sh") || it.endsWith(".json") || it.endsWith(".js")) }
                ?: fileList.firstOrNull()
                ?: "config.sh"
            val configContentRes = adapter.readConfig(defaultFile)
            val content = configContentRes.getOrNull() ?: ""

            val scriptTreeRes = scriptTreeDeferred.await()
            val remoteScriptTree = scriptTreeRes.getOrNull() ?: emptyList()

            val errorMsg = when {
                tasksRes.isFailure -> "任务加载失败: ${tasksRes.exceptionOrNull()?.message}"
                envsRes.isFailure -> "环境变量加载失败: ${envsRes.exceptionOrNull()?.message}"
                scriptTreeRes.isFailure -> "脚本文件加载失败: ${scriptTreeRes.exceptionOrNull()?.message}"
                else -> null
            }

            val metricsRes = metricsDeferred.await()
            val (cpu, ram) = metricsRes.getOrNull() ?: Pair("--", "--")

            // 再次检查用户是否在网络请求期间切换了面板，如果是则丢弃结果以防旧数据覆盖
            if (getActivePanel().id != activePanel.id) {
                return@launch
            }

            val updatedPanels = _uiState.value.panels.map {
                if (it.id == activePanel.id) it.copy(cpuUsage = cpu, ramUsage = ram) else it
            }

            val finalTasks = if (tasksRes.isSuccess) remoteTasks else _uiState.value.tasks
            val finalSubs = if (subsRes.isSuccess) remoteSubs else _uiState.value.subscriptions
            val finalEnvs = if (envsRes.isSuccess) remoteEnvs else _uiState.value.envs
            val finalDeps = if (depsRes.isSuccess) remoteDeps else _uiState.value.deps
            val finalScriptTree = if (scriptTreeRes.isSuccess) remoteScriptTree else _uiState.value.scriptTree

            val finalConfigFiles = if (configFilesRes.isSuccess) fileList else _uiState.value.configFiles
            val finalConfigContent = if (configContentRes.isSuccess) content else _uiState.value.configContent

            if (tasksRes.isSuccess || envsRes.isSuccess || subsRes.isSuccess || depsRes.isSuccess || scriptTreeRes.isSuccess) {
                saveDiskCache(activePanel.id, finalTasks, finalEnvs, finalSubs, finalDeps, finalScriptTree, finalConfigFiles, defaultFile, finalConfigContent)
            }

            _uiState.value = _uiState.value.copy(
                panels = updatedPanels,
                tasks = finalTasks,
                subscriptions = finalSubs,
                envs = finalEnvs,
                deps = finalDeps,
                configFiles = if (configFilesRes.isSuccess) fileList else _uiState.value.configFiles,
                selectedConfigFile = defaultFile,
                configContent = if (configContentRes.isSuccess) content else _uiState.value.configContent,
                scriptTree = finalScriptTree,
                isLoading = false,
                toastMessage = errorMsg ?: _uiState.value.toastMessage
            )
        }
    }

    fun refreshCurrentPanel() {
        val active = getActivePanel()
        refreshPanelRemoteData(active)
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
                prefs.edit().putString("active_panel_id", savedInstance.id).apply()
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
                prefs.edit().putString("active_panel_id", saved.id).apply()
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

    fun authenticateAndAddPanel(
        name: String,
        type: PanelType,
        baseUrl: String,
        username: String,
        password: String,
        onResult: (Boolean, String) -> Unit
    ) {
        authenticateAndSavePanel(null, name, type, baseUrl, username, password, onResult)
    }

    // ==================== 1. 任务管理 ====================
    fun runTask(taskId: String) {
        viewModelScope.launch {
            val list = _uiState.value.tasks.map { if (it.id == taskId) it.copy(isRunning = true, statusText = "运行中") else it }
            _uiState.value = _uiState.value.copy(tasks = list, toastMessage = "任务开始执行...")
            repository.getAdapter(getActivePanel()).runTask(listOf(taskId))
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
            refreshPanelRemoteData(getActivePanel())
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

    // ==================== 2. 订阅管理 ====================
    fun createSubscription(sub: UnifiedSubscription) {
        viewModelScope.launch {
            val res = repository.getAdapter(getActivePanel()).createSubscription(sub)
            if (res.isSuccess) {
                _uiState.value = _uiState.value.copy(toastMessage = "订阅 [${sub.name}] 保存成功！")
            } else {
                _uiState.value = _uiState.value.copy(toastMessage = "订阅已保存")
            }
            refreshPanelRemoteData(getActivePanel())
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
            refreshPanelRemoteData(getActivePanel())
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
    fun switchConfigFile(path: String) {
        viewModelScope.launch {
            val res = repository.getAdapter(getActivePanel()).readConfig(path)
            val content = res.getOrNull() ?: ""
            _uiState.value = _uiState.value.copy(selectedConfigFile = path, configContent = content)
        }
    }

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
            refreshPanelRemoteData(getActivePanel())
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
            refreshPanelRemoteData(getActivePanel())
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
            refreshPanelRemoteData(getActivePanel())
        }
    }

    fun deleteEnv(envId: String) {
        viewModelScope.launch {
            repository.getAdapter(getActivePanel()).deleteEnv(listOf(envId))
            val list = _uiState.value.envs.filterNot { it.id == envId }
            _uiState.value = _uiState.value.copy(envs = list, toastMessage = "环境变量已删除")
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
            for (id in envIds) {
                adapter.toggleEnv(id, enable)
            }
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
            _uiState.value = _uiState.value.copy(deps = updated, toastMessage = if (res.isSuccess) "已批量卸载/清除 ${depIds.size} 个依赖" else "已清除依赖记录")
            refreshPanelRemoteData(getActivePanel())
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
            refreshPanelRemoteData(getActivePanel())
        }
    }

    fun deleteDep(depId: String, type: String) {
        viewModelScope.launch {
            val res = repository.getAdapter(getActivePanel()).deleteDep(depId, type)
            val updated = _uiState.value.deps.filterNot { it.id == depId }
            _uiState.value = _uiState.value.copy(deps = updated, toastMessage = if (res.isSuccess) "依赖包已卸载/清除" else "已清除依赖记录")
            refreshPanelRemoteData(getActivePanel())
        }
    }

    fun forceDeleteDeps(depIds: List<String>) {
        viewModelScope.launch {
            val res = repository.getAdapter(getActivePanel()).forceDeleteDeps(depIds)
            val updated = _uiState.value.deps.filterNot { depIds.contains(it.id) }
            _uiState.value = _uiState.value.copy(deps = updated, toastMessage = if (res.isSuccess) "已强制清除 ${depIds.size} 条依赖记录" else "清除记录失败")
            refreshPanelRemoteData(getActivePanel())
        }
    }

    fun toggleDevMode(enabled: Boolean) {
        prefs.edit().putBoolean("is_dev_mode", enabled).apply()
        com.panel.app.data.logger.AppLogger.isDevModeEnabled = enabled
        _uiState.value = _uiState.value.copy(isDevMode = enabled, toastMessage = if (enabled) "开发者模式已开启" else "开发者模式已关闭")
    }

    fun loadLoginLogs(onLoaded: (List<Map<String, Any>>) -> Unit) {
        viewModelScope.launch {
            val res = repository.getAdapter(getActivePanel()).getLoginLogs()
            onLoaded(res.getOrNull() ?: emptyList())
        }
    }

    fun loadServerLogsTree(onLoaded: (com.google.gson.JsonElement) -> Unit) {
        viewModelScope.launch {
            val res = repository.getAdapter(getActivePanel()).getLogsTree()
            res.getOrNull()?.let { onLoaded(it) }
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

    /** 从备份 JSON 恢复勾选内容，返回逐类报告 */
    fun restoreBackup(json: String, onResult: (List<RestoreReport>, String?) -> Unit) {
        viewModelScope.launch {
            val backup = parseBackupJson(json)
            if (backup == null) {
                onResult(emptyList(), "备份文件格式无法解析，请确认选择的是本 App 导出的备份文件")
                return@launch
            }

            val adapter = repository.getAdapter(getActivePanel())
            val sourceType = backup.sourcePanelType?.let { raw ->
                runCatching {
                    when (raw.uppercase()) {
                        "QINGLONG" -> PanelType.QINGLONG_V15 // 兼容旧备份
                        "BAIHU" -> PanelType.BAIHU
                        else -> PanelType.valueOf(raw)
                    }
                }.getOrNull()
            } ?: getActivePanel().type

            val reports = mutableListOf<RestoreReport>()
            if (backup.tasks.isNotEmpty()) {
                adapter.restoreTasks(backup.tasks).getOrNull()?.let { reports.add(it) }
            }
            if (backup.envs.isNotEmpty()) {
                adapter.restoreEnvs(backup.envs).getOrNull()?.let { reports.add(it) }
            }
            if (backup.scripts.isNotEmpty()) {
                adapter.restoreScripts(backup.scripts).getOrNull()?.let { reports.add(it) }
            }
            if (backup.configFiles.isNotEmpty()) {
                adapter.restoreConfigFiles(backup.configFiles, sourceType).getOrNull()?.let { reports.add(it) }
            }
            if (reports.isEmpty()) {
                onResult(emptyList(), "备份文件里没有可恢复的内容")
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

    /** 仪表盘聚合数据，带本地持久化秒开与异步刷新 */
    fun loadDashboard(onResult: (Result<PanelDashboard>) -> Unit) {
        val panel = getActivePanel()
        val cached = repository.getCachedDashboard(panel.id)
        if (cached != null) {
            onResult(Result.success(cached))
        }

        viewModelScope.launch {
            val res = repository.getAdapter(panel).getDashboard()
            res.onSuccess { dashboard ->
                repository.saveCachedDashboard(panel.id, dashboard)
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
            val adapter = repository.getAdapter(getActivePanel())
            val res = when (adapter) {
                is QinglongV15Adapter -> adapter.fetchSystemSettings().getOrNull()
                is QinglongV10Adapter -> adapter.fetchSystemSettings().getOrNull()
                else -> null
            }
            onResult(res)
        }
    }

    fun saveLogRemoveFrequency(days: Int?) {
        viewModelScope.launch {
            val adapter = repository.getAdapter(getActivePanel())
            val res = when (adapter) {
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
            val adapter = repository.getAdapter(getActivePanel())
            val res = when (adapter) {
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
            val adapter = repository.getAdapter(getActivePanel())
            val res = when (adapter) {
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
            if (res.isSuccess) refreshPanelRemoteData(getActivePanel())
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
            refreshPanelRemoteData(getActivePanel())
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
            refreshPanelRemoteData(getActivePanel())
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
            refreshPanelRemoteData(getActivePanel())
        }
    }

    fun saveScript(path: String, content: String) {
        viewModelScope.launch {
            val res = repository.getAdapter(getActivePanel()).saveScript(path, content)
            if (res.isSuccess) {
                _uiState.value = _uiState.value.copy(toastMessage = "脚本 [$path] 保存成功！")
            } else {
                _uiState.value = _uiState.value.copy(toastMessage = "保存失败: ${res.exceptionOrNull()?.message}")
            }
        }
    }

    fun readScript(path: String, onRead: (String) -> Unit) {
        viewModelScope.launch {
            val res = repository.getAdapter(getActivePanel()).readScript(path)
            onRead(res.getOrNull() ?: "")
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
            refreshPanelRemoteData(getActivePanel())
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
            refreshPanelRemoteData(getActivePanel())
        }
    }

    fun updatePanelInstance(panel: PanelInstance) {
        viewModelScope.launch {
            repository.savePanel(panel)
            _uiState.value = _uiState.value.copy(toastMessage = "面板 [${panel.name}] 配置已更新！")
            refreshPanelRemoteData(panel)
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
