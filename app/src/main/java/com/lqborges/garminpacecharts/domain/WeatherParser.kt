package com.lqborges.garminpacecharts.domain

import com.lqborges.garminpacecharts.domain.model.WeatherSnapshot
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
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
        val apparent = current["apparent_temperature"]?.jsonPrimitive?.doubleOrNull ?: temperature
        val humidity = current["relative_humidity_2m"]?.jsonPrimitive?.intOrNull ?: return null
        val wind = current["wind_speed_10m"]?.jsonPrimitive?.doubleOrNull ?: 0.0
        val precipitation = current["precipitation"]?.jsonPrimitive?.doubleOrNull ?: 0.0
        val weatherCode = current["weather_code"]?.jsonPrimitive?.intOrNull ?: return null
        val observedAt = current["time"]?.jsonPrimitive?.content ?: ""

        return WeatherSnapshot(
            locationName = locationName,
            temperatureC = temperature,
            apparentTemperatureC = apparent,
            humidityPercent = humidity,
            windSpeedKmh = wind,
            precipitationMm = precipitation,
            weatherCode = weatherCode,
            observedAt = observedAt,
            fetchedAtEpochMillis = fetchedAtEpochMillis,
        )
    }
}