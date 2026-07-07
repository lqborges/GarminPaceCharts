package com.lqborges.garminpacecharts.data.repository

import com.lqborges.garminpacecharts.RefreshStatus
import com.lqborges.garminpacecharts.data.local.AppDatabase
import com.lqborges.garminpacecharts.data.local.PreferencesManager
import com.lqborges.garminpacecharts.data.local.entity.RefreshRunEntity
import com.lqborges.garminpacecharts.data.local.toDomain
import com.lqborges.garminpacecharts.data.local.toEntity
import com.lqborges.garminpacecharts.domain.ImportExport
import com.lqborges.garminpacecharts.domain.model.DashboardStats
import com.lqborges.garminpacecharts.domain.model.ImportResult
import com.lqborges.garminpacecharts.domain.model.Workout
import com.lqborges.garminpacecharts.domain.ChartDataBuilder
import com.lqborges.garminpacecharts.TrendDirection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Instant

class WorkoutRepository(
    private val database: AppDatabase,
    private val preferencesManager: PreferencesManager,
) {
    private val workoutDao = database.workoutDao()

    fun observeWorkouts(): Flow<List<Workout>> =
        workoutDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    suspend fun getWorkouts(): List<Workout> = workoutDao.getAll().map { it.toDomain() }

    suspend fun getWorkout(id: Long): Workout? = workoutDao.getById(id)?.toDomain()

    suspend fun deleteWorkout(id: Long) = workoutDao.deleteById(id)

    suspend fun importJson(raw: String): ImportResult {
        val (rows, invalid) = ImportExport.parseImportJson(raw)
        val existing = getWorkouts()
        val (merged, result) = ImportExport.mergeImports(existing, rows)
        workoutDao.upsertAll(merged.map { it.toEntity() })
        return result.copy(invalidRows = invalid)
    }

    suspend fun exportJson(): String = ImportExport.exportToJson(getWorkouts())

    suspend fun upsertWorkouts(workouts: List<Workout>) {
        workoutDao.upsertAll(workouts.map { it.toEntity() })
    }

    fun observeDashboardStats(garminConnected: Boolean): Flow<DashboardStats> =
        combine(
            observeWorkouts(),
            preferencesManager.lastRefreshAt,
        ) { workouts, lastRefresh ->
            val latest = workouts.maxByOrNull { it.startTimeLocal }
            val recentAvg = ChartDataBuilder.fourWeekAveragePace(workouts)
            val previousAvg = ChartDataBuilder.previousFourWeekAveragePace(workouts)
            val trend = when {
                recentAvg == null || previousAvg == null -> TrendDirection.INSUFFICIENT_DATA
                recentAvg < previousAvg - 0.05 -> TrendDirection.IMPROVING
                recentAvg > previousAvg + 0.05 -> TrendDirection.DECLINING
                else -> TrendDirection.STABLE
            }
            DashboardStats(
                totalWorkouts = workouts.size,
                latestWorkoutDate = latest?.startTimeLocal,
                latestPace = latest?.paceMinPerKm,
                fourWeekTrend = trend,
                fourWeekAveragePace = recentAvg,
                consecutiveWeekStreak = ChartDataBuilder.consecutiveWeekStreak(workouts),
                currentWeekAveragePace = ChartDataBuilder.currentWeekAveragePace(workouts),
                weeklyPaceRank = ChartDataBuilder.weeklyPaceRank(workouts),
                garminConnected = garminConnected,
                lastRefreshAt = lastRefresh?.let { Instant.ofEpochMilli(it) },
            )
        }

    suspend fun clearAll() {
        workoutDao.deleteAll()
        database.refreshRunDao().deleteAll()
        database.healthMetricDao().deleteAll()
        database.healthAssessmentDao().deleteAll()
    }
}
