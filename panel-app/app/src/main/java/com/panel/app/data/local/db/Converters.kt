package com.panel.app.data.local.db

import androidx.room.TypeConverter
import com.panel.app.data.model.PanelType

class Converters {
    @TypeConverter
    fun fromPanelType(type: PanelType?): String? = type?.name

    @TypeConverter
    fun toPanelType(value: String?): PanelType? = value?.let {
        try {
            PanelType.valueOf(it)
        } catch (_: Exception) {
            PanelType.BAIHU
        }
    }
}
