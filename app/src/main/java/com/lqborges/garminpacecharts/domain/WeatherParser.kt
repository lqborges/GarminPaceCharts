package com.lqborges.garminpacecharts.domain

import com.lqborges.garminpacecharts.domain.model.WeatherSnapshot
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object WeatherParser {
    fun fromOpenMeteo(
        body: JsonObject,
        locationName: String,
        fetchedAtEpochMillis: Long = System.currentTimeMillis(),
    ): WeatherSnapshot? {
        val current = body["current"]?.jsonObject ?: return null
        val temperature = current["temperature_2m"]?.jsonPrimitive?.doubleOrNull ?: return null
        val observedAt = current["time"]?.jsonPrimitive?.content ?: ""
        val rainChance = nextHourRainChance(observedAt, body["hourly"]?.jsonObject) ?: return null

        return WeatherSnapshot(
            locationName = locationName,
            temperatureC = temperature,
            rainChanceNextHourPercent = rainChance,
            observedAt = observedAt,
            fetchedAtEpochMillis = fetchedAtEpochMillis,
        )
    }

    internal fun nextHourRainChance(currentTime: String, hourly: JsonObject?): Int? {
        if (hourly == null || currentTime.length < 13) return null
        val times = hourly["time"]?.jsonArray ?: return null
        val probabilities = hourly["precipitation_probability"]?.jsonArray ?: return null
        val currentHourKey = currentTime.take(13)

        for (index in 0 until minOf(times.size, probabilities.size)) {
            val slotHourKey = times[index].jsonPrimitive.content.take(13)
            if (slotHourKey > currentHourKey) {
                return probabilities[index].jsonPrimitive.intOrNull
            }
        }
        return null
    }
}