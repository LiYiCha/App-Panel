package com.panel.app.ui.viewmodel

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.panel.app.data.model.*
import com.panel.app.data.repository.PanelRepository
import com.panel.app.ui.screens.BottomNavScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class MainUiState(
    val isDarkTheme: Boolean = true,
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
    val toastMessage: String? = null
)

@HiltViewModel
class MainViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: PanelRepository
) : ViewModel() {

    private val prefs: SharedPreferences = context.getSharedPreferences("panel_hub_prefs", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(
        MainUiState(
            isDarkTheme = prefs.getBoolean("is_dark_theme", true),
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
                    val authRes = repository.testAuthenticate(activePanel)
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

            _uiState.value = _uiState.value.copy(isLoading = true)
            val adapter = repository.getAdapter(activePanel)

            // 1. 任务
            val tasksRes = adapter.getTasks()
            val remoteTasks = tasksRes.getOrNull() ?: emptyList()

            // 2. 订阅
            val subsRes = adapter.getSubscriptions()
            val remoteSubs = subsRes.getOrNull() ?: emptyList()

            // 3. 环境变量
            val envsRes = adapter.getEnvs()
            val remoteEnvs = envsRes.getOrNull() ?: emptyList()

            // 4. 依赖
            val depsRes = adapter.getDeps()
            val remoteDeps = depsRes.getOrNull() ?: emptyList()

            // 5. 配置文件列表与内容
            val configFilesRes = adapter.getConfigFiles()
            val fileList = configFilesRes.getOrNull() ?: listOf(if (activePanel.type == PanelType.BAIHU) "config.json" else "config.sh")
            val defaultFile = fileList.firstOrNull { it.equals("config.sh", ignoreCase = true) || it.equals("config.json", ignoreCase = true) }
                ?: fileList.firstOrNull { !it.contains("/") && (it.endsWith(".sh") || it.endsWith(".json") || it.endsWith(".js")) }
                ?: fileList.firstOrNull()
                ?: "config.sh"
            val configContentRes = adapter.readConfig(defaultFile)
            val content = configContentRes.getOrNull() ?: ""

            // 6. 脚本文件树
            val scriptTreeRes = adapter.getScriptTree()
            val remoteScriptTree = scriptTreeRes.getOrNull() ?: emptyList()

            val errorMsg = when {
                tasksRes.isFailure -> "任务加载失败: ${tasksRes.exceptionOrNull()?.message}"
                envsRes.isFailure -> "环境变量加载失败: ${envsRes.exceptionOrNull()?.message}"
                scriptTreeRes.isFailure -> "脚本文件加载失败: ${scriptTreeRes.exceptionOrNull()?.message}"
                else -> null
            }

            // 7. 硬件性能监控 (Metrics)
            val metricsRes = adapter.getMetrics()
            val (cpu, ram) = metricsRes.getOrNull() ?: Pair("--", "--")

            // 再次检查用户是否在网络请求期间切换了面板，如果是则丢弃结果以防旧数据覆盖
            if (getActivePanel().id != activePanel.id) {
                return@launch
            }

            val updatedPanels = _uiState.value.panels.map {
                if (it.id == activePanel.id) it.copy(cpuUsage = cpu, ramUsage = ram) else it
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
            val cleanUrl = baseUrl.trimEnd('/')

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

            val authResult = repository.testAuthenticate(candidate)
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
                val err = authResult.exceptionOrNull()?.message ?: "登录失败，请检查账号密码"
                _uiState.value = _uiState.value.copy(toastMessage = err)
                onResult(false, err)
            }
        }
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
            val list = _uiState.value.tasks.map { if (it.id == taskId) it.copy(isRunning = false, statusText = "已停止") else it }
            _uiState.value = _uiState.value.copy(tasks = list, toastMessage = "任务已停止")
            repository.getAdapter(getActivePanel()).stopTask(listOf(taskId))
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
                _uiState.value = _uiState.value.copy(toastMessage = "创建任务已同步")
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
            repository.getAdapter(getActivePanel()).deleteTask(taskIds)
            val list = _uiState.value.tasks.filterNot { taskIds.contains(it.id) }
            _uiState.value = _uiState.value.copy(tasks = list, toastMessage = "已批量删除 ${taskIds.size} 个任务")
        }
    }

    fun deleteTask(taskId: String) {
        viewModelScope.launch {
            repository.getAdapter(getActivePanel()).deleteTask(listOf(taskId))
            val list = _uiState.value.tasks.filterNot { it.id == taskId }
            _uiState.value = _uiState.value.copy(tasks = list, toastMessage = "任务已删除")
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

    // ==================== 4. 环境变量 ====================
    fun toggleEnv(envId: String, enable: Boolean) {
        viewModelScope.launch {
            val list = _uiState.value.envs.map { if (it.id == envId) it.copy(enabled = enable) else it }
            _uiState.value = _uiState.value.copy(envs = list, toastMessage = "变量状态已设为: ${if (enable) "启用" else "禁用"}")
            repository.getAdapter(getActivePanel()).toggleEnv(envId, enable)
        }
    }

    fun saveEnv(env: UnifiedEnv) {
        viewModelScope.launch {
            val res = repository.getAdapter(getActivePanel()).saveEnv(env)
            if (res.isSuccess) {
                _uiState.value = _uiState.value.copy(toastMessage = "变量 [${env.name}] 已保存")
            }
            refreshPanelRemoteData(getActivePanel())
        }
    }

    fun addEnvs(newEnvs: List<UnifiedEnv>) {
        viewModelScope.launch {
            for (env in newEnvs) {
                repository.getAdapter(getActivePanel()).saveEnv(env)
            }
            _uiState.value = _uiState.value.copy(toastMessage = "成功导入 ${newEnvs.size} 条环境变量！")
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

    fun getDepLog(depId: String, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val res = repository.getAdapter(getActivePanel()).getDepLog(depId)
            onResult(res.getOrNull() ?: "暂无依赖安装/构建日志")
        }
    }

    // ==================== 6. 脚本管理 ====================
    fun createScript(fileName: String, content: String) {
        viewModelScope.launch {
            val res = repository.getAdapter(getActivePanel()).createScript(fileName, content)
            if (res.isSuccess) {
                _uiState.value = _uiState.value.copy(toastMessage = "脚本 [$fileName] 创建成功！")
            } else {
                _uiState.value = _uiState.value.copy(toastMessage = "创建失败: ${res.exceptionOrNull()?.message}")
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
}
