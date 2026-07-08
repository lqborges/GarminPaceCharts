package com.lqborges.garminpacecharts.domain

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

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
}