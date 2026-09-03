package com.panel.app.data.repository

import android.content.Context
import com.panel.app.data.adapter.IPanelAdapter
import com.panel.app.data.adapter.PanelAdapterFactory
import com.panel.app.data.local.db.AppDatabase
import com.panel.app.data.model.PanelInstance
import com.panel.app.util.PanelUrl
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PanelRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val db = AppDatabase.getDatabase(context)
    private val dao = db.panelDao()

    val panelsFlow: Flow<List<PanelInstance>> = dao.getAllPanels()

    suspend fun savePanel(panel: PanelInstance) {
        dao.insertPanel(panel)
    }

    suspend fun deletePanel(id: String) {
        dao.deleteById(id)
        invalidateAdapter(id)
    }

    /**
     * 适配器缓存：panelId -> 适配器实例，以及该实例创建时的配置指纹。
     *
     * 适配器在构造阶段就要建 Retrofit 并生成 API 动态代理（反射解析接口全部方法），
     * 属于重量级对象。而 MainViewModel 里 repository.getAdapter() 有 60+ 处调用，
     * 日志跟随、任务状态轮询这类高频场景会在几秒内反复"创建—丢弃"整条链路，
     * 产生大量一次性对象与 GC 压力 —— 这正是界面闪烁/花屏的根因。
     *
     * 这里按 panelId 复用实例；地址、类型或凭据一旦变化，指纹不匹配即重建，
     * 保证不会沿用旧的连接配置或过期 token。缓存条目数等于面板数量，天然有界。
     */
    private val adapterCache = ConcurrentHashMap<String, IPanelAdapter>()
    private val adapterFingerprints = ConcurrentHashMap<String, String>()

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
        val fingerprint = listOf(
            safePanel.type.name,
            safePanel.baseUrl,
            safePanel.username.orEmpty(),
            safePanel.password.orEmpty(),
            safePanel.token.orEmpty()
        ).joinToString("|")

        synchronized(this) {
            val cached = adapterCache[safePanel.id]
            if (cached != null && adapterFingerprints[safePanel.id] == fingerprint) {
                return cached
            }
            val created = PanelAdapterFactory.create(safePanel)
            adapterCache[safePanel.id] = created
            adapterFingerprints[safePanel.id] = fingerprint
            return created
        }
    }

    /** 面板被删除或凭据被清空时调用，避免缓存里留下失效的适配器 */
    fun invalidateAdapter(panelId: String) {
        synchronized(this) {
            adapterCache.remove(panelId)
            adapterFingerprints.remove(panelId)
        }
    }

    suspend fun testAuthenticate(panel: PanelInstance): Result<String> {
        val adapter = getAdapter(panel)
        return adapter.authenticate()
    }

    private val gson = com.google.gson.Gson()

    /** 统一缓存目录，所有缓存数据集中存储，避免分散在各层 */
    private val cacheDir: java.io.File
        get() = java.io.File(context.filesDir, "cache")

    fun getCachedDashboard(panelId: String): com.panel.app.data.model.PanelDashboard? {
        try {
            val file = java.io.File(cacheDir, "dashboard_cache_${panelId}.json")
            if (file.exists()) {
                val json = file.readText()
                return runCatching { gson.fromJson(json, com.panel.app.data.model.PanelDashboard::class.java) }.getOrNull()
            }
        } catch (_: Exception) {}
        return null
    }

    fun saveCachedDashboard(panelId: String, dashboard: com.panel.app.data.model.PanelDashboard) {
        try {
            cacheDir.mkdirs()
            val file = java.io.File(cacheDir, "dashboard_cache_${panelId}.json")
            file.writeText(gson.toJson(dashboard))
        } catch (_: Exception) {}
    }
}
