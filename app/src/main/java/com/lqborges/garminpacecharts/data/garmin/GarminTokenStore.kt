package com.lqborges.garminpacecharts.data.garmin

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class GarminTokenStore(context: Context) {
    private val json = Json { ignoreUnknownKeys = true }

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "garmin_tokens",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun hasTokens(): Boolean = prefs.contains(KEY_DI_TOKEN)

    fun saveFromJson(raw: String) {
        val obj = json.parseToJsonElement(raw).jsonObject
        val diToken = obj["di_token"]?.jsonPrimitive?.content
            ?: obj["oauth_token"]?.jsonPrimitive?.content
            ?: error("Missing di_token in token file")
        val refresh = obj["di_refresh_token"]?.jsonPrimitive?.content
            ?: obj["oauth_token_secret"]?.jsonPrimitive?.content
        val clientId = obj["di_client_id"]?.jsonPrimitive?.content

        prefs.edit()
            .putString(KEY_DI_TOKEN, diToken)
            .putString(KEY_REFRESH_TOKEN, refresh)
            .putString(KEY_CLIENT_ID, clientId)
            .apply()
    }

    fun saveTokens(diToken: String, refreshToken: String?, clientId: String?) {
        prefs.edit()
            .putString(KEY_DI_TOKEN, diToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .putString(KEY_CLIENT_ID, clientId)
            .apply()
    }

    fun getDiToken(): String? = prefs.getString(KEY_DI_TOKEN, null)
    fun getRefreshToken(): String? = prefs.getString(KEY_REFRESH_TOKEN, null)
    fun getClientId(): String? = prefs.getString(KEY_CLIENT_ID, null)

    fun clear() {
        prefs.edit().clear().apply()
    }

    fun exportRedactedSummary(): String =
        if (hasTokens()) "Garmin tokens: present (redacted)" else "Garmin tokens: not configured"

    companion object {
        private const val KEY_DI_TOKEN = "di_token"
        private const val KEY_REFRESH_TOKEN = "di_refresh_token"
        private const val KEY_CLIENT_ID = "di_client_id"
    }
}
