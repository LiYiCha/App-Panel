package com.panel.app.data.repository

import android.content.Context
import com.panel.app.data.adapter.IPanelAdapter
import com.panel.app.data.adapter.PanelAdapterFactory
import com.panel.app.data.local.db.AppDatabase
import com.panel.app.data.model.PanelInstance
import com.panel.app.data.model.PanelType
import com.panel.app.util.PanelUrl
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PanelRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val db = AppDatabase.getDatabase(context)
    private val dao = db.panelDao()

    val panelsFlow: Flow<List<PanelInstance>> = dao.getAllPanels()

    suspend fun initDefaultPanelsIfEmpty() {
        // 不再预置未登录的假数据，由登录页真实接入
    }

    suspend fun savePanel(panel: PanelInstance) {
        dao.insertPanel(panel)
    }

    suspend fun deletePanel(id: String) {
        dao.deleteById(id)
    }

    /**
     * 获取面板适配器。
     *
     * 三个适配器都在**构造阶段**用 `baseUrl` 去建 Retrofit，
     * 而 Retrofit 的 `baseUrl()` 遇到不可解析地址会抛 `IllegalArgumentException`
     * （典型：用户填了 `192.168.1.100:5700` 却漏了 `http://`）。
     * 该异常发生在构造器中，业务代码的 try/catch 包不到，会直接把 App 打崩，
     * 所以必须在进工厂前把地址清洗成合法绝对地址。
     */
    fun getAdapter(panel: PanelInstance): IPanelAdapter {
        val safeUrl = PanelUrl.sanitize(panel.baseUrl)
        val safePanel = if (safeUrl == panel.baseUrl) panel else panel.copy(baseUrl = safeUrl)
        return PanelAdapterFactory.create(safePanel)
    }

    suspend fun testAuthenticate(panel: PanelInstance): Result<String> {
        val adapter = getAdapter(panel)
        return adapter.authenticate()
    }

    private val prefs = context.getSharedPreferences("dashboard_cache_prefs", Context.MODE_PRIVATE)
    private val gson = com.google.gson.Gson()

    fun getCachedDashboard(panelId: String): com.panel.app.data.model.PanelDashboard? {
        val json = prefs.getString("dashboard_$panelId", null) ?: return null
        return runCatching { gson.fromJson(json, com.panel.app.data.model.PanelDashboard::class.java) }.getOrNull()
    }

    fun saveCachedDashboard(panelId: String, dashboard: com.panel.app.data.model.PanelDashboard) {
        runCatching {
            val json = gson.toJson(dashboard)
            prefs.edit().putString("dashboard_$panelId", json).apply()
        }
    }
}
