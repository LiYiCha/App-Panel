package com.panel.app.data.local.db

import androidx.room.*
import com.panel.app.data.model.PanelInstance
import kotlinx.coroutines.flow.Flow

@Dao
interface PanelDao {
    @Query("SELECT * FROM panel_instances")
    fun getAllPanels(): Flow<List<PanelInstance>>

    @Query("SELECT * FROM panel_instances WHERE id = :id LIMIT 1")
    suspend fun getPanelById(id: String): PanelInstance?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPanel(panel: PanelInstance)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(panels: List<PanelInstance>)

    @Update
    suspend fun updatePanel(panel: PanelInstance)

    @Delete
    suspend fun deletePanel(panel: PanelInstance)

    @Query("DELETE FROM panel_instances WHERE id = :id")
    suspend fun deleteById(id: String)
}
