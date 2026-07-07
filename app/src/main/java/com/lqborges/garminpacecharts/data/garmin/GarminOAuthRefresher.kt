package com.lqborges.garminpacecharts.data.garmin

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Base64
import java.util.concurrent.TimeUnit

object GarminJwt {
    private val json = Json { ignoreUnknownKeys = true }

    fun expiresSoon(token: String, skewSeconds: Long = 900): Boolean {
        val payloadPart = token.split(".").getOrNull(1) ?: return true
        return runCatching {
            val padded = payloadPart + "=".repeat((4 - payloadPart.length % 4) % 4)
            val decoded = Base64.getUrlDecoder().decode(padded)
            val exp = json.parseToJsonElement(String(decoded)).jsonObject["exp"]?.jsonPrimitive?.longOrNull
                ?: return true
            val now = System.currentTimeMillis() / 1000
            now > exp - skewSeconds
        }.getOrDefault(true)
    }

    fun clientIdFromJwt(token: String): String? {
        val payloadPart = token.split(".").getOrNull(1) ?: return null
        return runCatching {
            val padded = payloadPart + "=".repeat((4 - payloadPart.length % 4) % 4)
            val decoded = Base64.getUrlDecoder().decode(padded)
            json.parseToJsonElement(String(decoded)).jsonObject["client_id"]?.jsonPrimitive?.content
        }.getOrNull()
    }
}

class GarminOAuthRefresher(
    private val tokenStore: GarminTokenStore,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build(),
) {
    private val json = Json { ignoreUnknownKeys = true }

    @Synchronized
    fun ensureValidAccessToken(forceRefresh: Boolean = false) {
        val current = tokenStore.getDiToken() ?: throw GarminApiException(
            "Missing Garmin token",
            "NOT_CONFIGURED",
        )
        if (!forceRefresh && !GarminJwt.expiresSoon(current)) return

        val refreshToken = tokenStore.getRefreshToken()
            ?: throw GarminApiException(
                "Garmin access token expired and no refresh token is stored. Re-import tokens.json.",
                "AUTH_FAILED",
            )
        val clientId = tokenStore.getClientId()
            ?: GarminJwt.clientIdFromJwt(current)
            ?: throw GarminApiException(
                "Garmin client id missing. Re-import tokens.json.",
                "AUTH_FAILED",
            )

        val body = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("client_id", clientId)
            .add("refresh_token", refreshToken)
            .build()

        val basic = Base64.getEncoder().encodeToString("$clientId:".toByteArray())
        val request = Request.Builder()
            .url(DI_TOKEN_URL)
            .header("Authorization", "Basic $basic")
            .header("Accept", "application/json")
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Cache-Control", "no-cache")
            .headers(GarminApiHeaders.native())
            .post(body)
            .build()

        httpClient.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw GarminApiException(
                    "Garmin token refresh failed (${response.code}). Re-import a fresh tokens.json from your PC.",
                    "AUTH_FAILED",
                )
            }
            val data = json.parseToJsonElement(responseBody).jsonObject
            val accessToken = data["access_token"]?.jsonPrimitive?.content
                ?: throw GarminApiException("Garmin refresh response missing access_token", "AUTH_FAILED")
            val newRefresh = data["refresh_token"]?.jsonPrimitive?.content ?: refreshToken
            val newClientId = GarminJwt.clientIdFromJwt(accessToken) ?: clientId
            tokenStore.saveTokens(accessToken, newRefresh, newClientId)
        }
    }

    companion object {
        private const val DI_TOKEN_URL = "https://diauth.garmin.com/di-oauth2-service/oauth/token"
    }
}
