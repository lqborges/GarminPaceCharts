package com.lqborges.garminpacecharts.data.repository

import com.lqborges.garminpacecharts.data.local.PreferencesManager
import com.lqborges.garminpacecharts.data.weather.OpenMeteoClient
import com.lqborges.garminpacecharts.domain.RunningLocations
import com.lqborges.garminpacecharts.domain.WeatherParser
import com.lqborges.garminpacecharts.domain.model.WeatherSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class WeatherRepository(
    private val openMeteoClient: OpenMeteoClient,
    private val preferencesManager: PreferencesManager,
) {
    val snapshot: Flow<WeatherSnapshot?> = preferencesManager.weatherSnapshot

    suspend fun refreshIfStale(
        maxAgeMinutes: Long = 60,
        latitude: Double = RunningLocations.SHEFFIELD_LAT,
        longitude: Double = RunningLocations.SHEFFIELD_LON,
        locationName: String = RunningLocations.SHEFFIELD_NAME,
    ): WeatherSnapshot? = withContext(Dispatchers.IO) {
        val cached = preferencesManager.getWeatherSnapshot()
        if (cached != null && ageMinutes(cached) < maxAgeMinutes) {
            return@withContext cached
        }
        refresh(latitude, longitude, locationName)
    }

    suspend fun refresh(
        latitude: Double = RunningLocations.SHEFFIELD_LAT,
        longitude: Double = RunningLocations.SHEFFIELD_LON,
        locationName: String = RunningLocations.SHEFFIELD_NAME,
    ): WeatherSnapshot? = withContext(Dispatchers.IO) {
        val body = openMeteoClient.fetchCurrent(latitude, longitude) ?: return@withContext null
        val snapshot = WeatherParser.fromOpenMeteo(body, locationName) ?: return@withContext null
        preferencesManager.setWeatherSnapshot(snapshot)
        snapshot
    }

    private fun ageMinutes(snapshot: WeatherSnapshot): Long =
        (System.currentTimeMillis() - snapshot.fetchedAtEpochMillis) / 60_000
}