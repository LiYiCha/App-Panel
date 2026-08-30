package com.panel.app.data.adapter

import com.panel.app.data.model.PanelInstance
import com.panel.app.data.model.PanelType

object PanelAdapterFactory {
    fun create(instance: PanelInstance): IPanelAdapter {
        return when (instance.type) {
            PanelType.BAIHU -> BaihuPanelAdapter(instance)
            PanelType.QINGLONG_V15 -> QinglongV15Adapter(instance)
            PanelType.QINGLONG_V10 -> QinglongV10Adapter(instance)
        }
    }
}
