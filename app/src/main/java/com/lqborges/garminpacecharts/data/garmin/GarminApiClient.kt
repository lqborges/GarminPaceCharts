package com.lqborges.garminpacecharts.data.garmin

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.LocalDate
import java.util.concurrent.TimeUnit

class GarminApiClient(
    private val tokenStore: GarminTokenStore,
    private val oauthRefresher: GarminOAuthRefresher = GarminOAuthRefresher(tokenStore),
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build(),
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val baseUrl = "https://connectapi.garmin.com"

    fun isConfigured(): Boolean = tokenStore.hasTokens()

    suspend fun fetchActivitiesByDate(startDate: LocalDate, endDate: LocalDate): List<JsonObject> {
        if (!tokenStore.hasTokens()) {
            throw GarminApiException("Garmin tokens not configured. Import tokens in Settings.", "NOT_CONFIGURED")
        }

        val activities = mutableListOf<JsonObject>()
        var start = 0
        val limit = 20

        while (true) {
            val path = buildString {
                append("/activitylist-service/activities/search/activities")
                append("?startDate=$startDate")
                append("&endDate=$endDate")
                append("&start=$start")
                append("&limit=$limit")
            }
            val batch = connectApi(path)
            if (batch.isEmpty()) break
            activities.addAll(batch)
            if (batch.size < limit) break
            start += limit
        }

        return activities
    }

    suspend fun fetchActivity(activityId: Long): JsonObject {
        val path = "/activity-service/activity/$activityId"
        return connectApiRaw(path).jsonObject
    }

    suspend fun fetchDailySummary(date: LocalDate): JsonObject? {
        return runCatching {
            connectApiRaw("/usersummary-service/usersummary/daily?calendarDate=$date").jsonObject
        }.getOrNull()
    }

    private fun connectApi(path: String): List<JsonObject> {
        val element = connectApiRaw(path)
        return when (element) {
            is JsonArray -> element.mapNotNull { it as? JsonObject }
            is JsonObject -> {
                val list = element["activityList"]?.jsonArray
                list?.mapNotNull { it as? JsonObject } ?: listOf(element)
            }
            else -> emptyList()
        }
    }

    private fun connectApiRaw(path: String, allowRetry: Boolean = true): JsonElement {
        oauthRefresher.ensureValidAccessToken()
        val token = tokenStore.getDiToken() ?: throw GarminApiException("Missing Garmin token", "NOT_CONFIGURED")

        val request = Request.Builder()
            .url("$baseUrl$path")
            .headers(GarminApiHeaders.connectApi(token))
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (response.code == 401 && allowRetry) {
                oauthRefresher.ensureValidAccessToken(forceRefresh = true)
                return connectApiRaw(path, allowRetry = false)
            }
            if (response.code == 401) {
                throw GarminApiException(
                    "Garmin authentication failed after token refresh. Re-import tokens.json from your PC.",
                    "AUTH_FAILED",
                )
            }
            if (!response.isSuccessful) {
                throw GarminApiException("Garmin API error ${response.code}", "HTTP_${response.code}")
            }
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) return JsonArray(emptyList())
            return json.parseToJsonElement(body)
        }
    }
}
