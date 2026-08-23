package com.lqborges.garminpacecharts.domain

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate

data class DailyMetricValue(
    val date: LocalDate,
    val value: Double,
)

object WellnessMetricParser {
    fun vo2FromMaxMet(element: JsonElement?): Double? {
        if (element == null) return null
        val entries = when (element) {
            is JsonArray -> element.mapNotNull { it as? JsonObject }
            is JsonObject -> listOf(element)
            else -> emptyList()
        }
        val generic = entries.firstOrNull()?.get("generic")?.jsonObject ?: return null
        return generic["vo2MaxPreciseValue"]?.jsonPrimitive?.doubleOrNull
            ?: generic["vo2MaxValue"]?.jsonPrimitive?.doubleOrNull
    }

    fun formatVo2(value: Double): String =
        if (value == value.toInt().toDouble()) value.toInt().toString() else "%.1f".format(value)

    /**
     * Parse `/userstats-service/wellness/daily?...&metricId=60` resting-HR series.
     * Shape: allMetrics.metricsMap.WELLNESS_RESTING_HEART_RATE[{calendarDate, value}, ...]
     */
    fun restingHrSeriesFromUserStats(element: JsonElement?): List<DailyMetricValue> {
        val root = element as? JsonObject ?: return emptyList()
        val series = root["allMetrics"]?.jsonObject
            ?.get("metricsMap")?.jsonObject
            ?.get("WELLNESS_RESTING_HEART_RATE")?.jsonArray
            ?: return emptyList()
        return series.mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            val dateStr = obj["calendarDate"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val value = obj["value"]?.jsonPrimitive?.doubleOrNull
                ?: obj["value"]?.jsonPrimitive?.intOrNull?.toDouble()
                ?: return@mapNotNull null
            if (value <= 0.0) return@mapNotNull null
            runCatching { DailyMetricValue(LocalDate.parse(dateStr), value) }.getOrNull()
        }
    }
}