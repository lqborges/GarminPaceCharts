package com.lqborges.garminpacecharts.data.weather

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class OpenMeteoClient(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build(),
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun fetchCurrent(latitude: Double, longitude: Double): JsonObject? {
        val url = buildString {
            append("https://api.open-meteo.com/v1/forecast")
            append("?latitude=$latitude")
            append("&longitude=$longitude")
            append("&current=temperature_2m")
            append("&hourly=precipitation_probability")
            append("&forecast_hours=6")
            append("&timezone=Europe%2FLondon")
        }
        val request = Request.Builder().url(url).get().build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) return null
            return json.parseToJsonElement(body).jsonObject
        }
    }
}