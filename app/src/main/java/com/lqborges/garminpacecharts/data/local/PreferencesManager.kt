package com.lqborges.garminpacecharts.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lqborges.garminpacecharts.domain.model.WeatherSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "app_preferences")

class PreferencesManager(private val context: Context) {
    private object Keys {
        val SETUP_COMPLETE = booleanPreferencesKey("setup_complete")
        val ACTIVITY_FILTER = stringPreferencesKey("activity_filter")
        val PACE_MIN = doublePreferencesKey("pace_min")
        val PACE_MAX = doublePreferencesKey("pace_max")
        val LAST_REFRESH_AT = longPreferencesKey("last_refresh_at")
        val WEATHER_SNAPSHOT_JSON = stringPreferencesKey("weather_snapshot_json")
    }

    private val json = Json { ignoreUnknownKeys = true }

    val setupComplete: Flow<Boolean> = context.dataStore.data.map { it[Keys.SETUP_COMPLETE] ?: false }
    val activityFilter: Flow<String> = context.dataStore.data.map {
        it[Keys.ACTIVITY_FILTER] ?: com.lqborges.garminpacecharts.domain.PaceExtractor.DEFAULT_ACTIVITY_FILTER
    }
    val paceMin: Flow<Double> = context.dataStore.data.map {
        it[Keys.PACE_MIN] ?: com.lqborges.garminpacecharts.domain.PaceExtractor.MIN_PACE
    }
    val paceMax: Flow<Double> = context.dataStore.data.map {
        it[Keys.PACE_MAX] ?: com.lqborges.garminpacecharts.domain.PaceExtractor.MAX_PACE
    }
    val lastRefreshAt: Flow<Long?> = context.dataStore.data.map { it[Keys.LAST_REFRESH_AT] }

    val weatherSnapshot: Flow<WeatherSnapshot?> = context.dataStore.data.map { prefs ->
        prefs[Keys.WEATHER_SNAPSHOT_JSON]?.let { raw ->
            runCatching { json.decodeFromString<WeatherSnapshot>(raw) }.getOrNull()
        }
    }

    suspend fun setSetupComplete(value: Boolean) {
        context.dataStore.edit { it[Keys.SETUP_COMPLETE] = value }
    }

    suspend fun setActivityFilter(value: String) {
        context.dataStore.edit { it[Keys.ACTIVITY_FILTER] = value }
    }

    suspend fun setPaceRange(min: Double, max: Double) {
        context.dataStore.edit {
            it[Keys.PACE_MIN] = min
            it[Keys.PACE_MAX] = max
        }
    }

    suspend fun setLastRefreshAt(epochMillis: Long) {
        context.dataStore.edit { it[Keys.LAST_REFRESH_AT] = epochMillis }
    }

    suspend fun getWeatherSnapshot(): WeatherSnapshot? = weatherSnapshot.first()

    suspend fun setWeatherSnapshot(snapshot: WeatherSnapshot) {
        context.dataStore.edit {
            it[Keys.WEATHER_SNAPSHOT_JSON] = json.encodeToString(snapshot)
        }
    }
}
