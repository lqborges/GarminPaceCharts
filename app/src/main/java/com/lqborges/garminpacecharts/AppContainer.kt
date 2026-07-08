package com.lqborges.garminpacecharts

import android.content.Context
import com.lqborges.garminpacecharts.data.garmin.GarminApiClient
import com.lqborges.garminpacecharts.data.garmin.GarminTokenStore
import com.lqborges.garminpacecharts.data.local.AppDatabase
import com.lqborges.garminpacecharts.data.local.PreferencesManager
import com.lqborges.garminpacecharts.data.repository.HealthRepository
import com.lqborges.garminpacecharts.data.repository.RefreshRepository
import com.lqborges.garminpacecharts.data.repository.WeatherRepository
import com.lqborges.garminpacecharts.data.repository.WorkoutRepository
import com.lqborges.garminpacecharts.data.weather.OpenMeteoClient

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val database = AppDatabase.create(appContext)
    val preferencesManager = PreferencesManager(appContext)
    val garminTokenStore = GarminTokenStore(appContext)
    val garminApiClient = GarminApiClient(garminTokenStore)

    val workoutRepository = WorkoutRepository(database, preferencesManager)
    val healthRepository = HealthRepository(database, workoutRepository)
    val weatherRepository = WeatherRepository(OpenMeteoClient(), preferencesManager)
    val refreshRepository = RefreshRepository(
        database = database,
        workoutRepository = workoutRepository,
        healthRepository = healthRepository,
        garminApiClient = garminApiClient,
        preferencesManager = preferencesManager,
    )

    fun isGarminConnected(): Boolean = garminTokenStore.hasTokens()
}
