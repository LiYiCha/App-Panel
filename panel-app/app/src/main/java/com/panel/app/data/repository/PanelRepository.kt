package com.panel.app.data.repository

import android.content.Context
import com.panel.app.data.adapter.IPanelAdapter
import com.panel.app.data.adapter.PanelAdapterFactory
import com.panel.app.data.local.db.AppDatabase
import com.panel.app.data.model.PanelInstance
import com.panel.app.data.model.PanelType
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

    fun getAdapter(panel: PanelInstance): IPanelAdapter {
        return PanelAdapterFactory.create(panel)
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
