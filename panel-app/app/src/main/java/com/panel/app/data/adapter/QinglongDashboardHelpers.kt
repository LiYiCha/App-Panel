package com.panel.app.data.adapter

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.panel.app.data.model.LabelStat
import com.panel.app.data.model.TaskRank
import com.panel.app.data.model.TrendPoint

/**
 * 青龙仪表盘数据解析辅助函数（提取自 QinglongV15Adapter）。
 *
 * 包含：parseTrendArray、parseRankArray、parseLabelArray，
 * 以及 getDashboard 的主聚合逻辑。
 */
object QinglongDashboardHelpers {

    private fun JsonElement?.safeInt(): Int? = this?.takeIf { !it.isJsonNull }?.asInt
    private fun JsonElement?.safeLong(): Long? = this?.takeIf { !it.isJsonNull }?.asLong
    private fun JsonElement?.safeString(): String? = this?.takeIf { !it.isJsonNull }?.asString

    fun parseTrendArray(data: JsonElement?): List<TrendPoint> {
        if (data?.isJsonArray != true) return emptyList()
        return data.asJsonArray.mapNotNull { elem ->
            if (!elem.isJsonObject) return@mapNotNull null
            val obj = elem.asJsonObject
            TrendPoint(
                date = obj.get("date")?.safeString() ?: "",
                total = obj.get("total")?.safeInt() ?: 0,
                success = obj.get("success")?.safeInt() ?: 0,
                fail = obj.get("fail")?.safeInt() ?: 0
            )
        }
    }

    inline fun parseRankArray(
        data: JsonElement?,
        crossinline map: (JsonObject) -> TaskRank
    ): List<TaskRank> {
        if (data?.isJsonArray != true) return emptyList()
        return data.asJsonArray.mapNotNull { if (it.isJsonObject) map(it.asJsonObject) else null }
    }

    fun parseLabelArray(data: JsonElement?): List<LabelStat> {
        if (data?.isJsonArray != true) return emptyList()
        return data.asJsonArray.mapNotNull { elem ->
            if (!elem.isJsonObject) return@mapNotNull null
            val obj = elem.asJsonObject
            LabelStat(
                label = obj.get("label")?.safeString() ?: "未分类",
                count = obj.get("count")?.safeInt() ?: 0,
                todayRuns = obj.get("todayRuns")?.safeInt() ?: 0,
                successRate = obj.get("successRate")?.safeString()?.let { "$it%" },
                avgTimeMs = obj.get("avgTime")?.safeLong()
            )
        }
    }
}
