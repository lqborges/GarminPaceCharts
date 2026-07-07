package com.lqborges.garminpacecharts.data.repository

import com.lqborges.garminpacecharts.data.local.AppDatabase
import com.lqborges.garminpacecharts.data.local.toDomain
import com.lqborges.garminpacecharts.data.local.toEntity
import com.lqborges.garminpacecharts.domain.HealthAssessmentEngine
import com.lqborges.garminpacecharts.domain.model.HealthAssessment
import com.lqborges.garminpacecharts.domain.model.Workout
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant

class HealthRepository(
    private val database: AppDatabase,
    private val workoutRepository: WorkoutRepository,
) {
    private val metricDao = database.healthMetricDao()
    private val assessmentDao = database.healthAssessmentDao()

    fun observeLatestAssessment(): Flow<HealthAssessment?> =
        assessmentDao.observeLatest().map { it?.toDomain() }

    suspend fun getLatestAssessment(): HealthAssessment? =
        assessmentDao.getLatest()?.toDomain()

    suspend fun regenerateAssessment(): HealthAssessment {
        val workouts = workoutRepository.getWorkouts()
        val metrics = metricDao.getAll().map { it.toDomain() }
        val assessment = HealthAssessmentEngine.generate(workouts, metrics)
        assessmentDao.insert(assessment.toEntity())
        return assessment
    }

    /** Regenerate when workouts or a Garmin refresh are newer than the cached assessment. */
    suspend fun regenerateIfStale(
        workouts: List<Workout>,
        lastRefreshAt: Instant? = null,
    ): HealthAssessment? {
        if (workouts.isEmpty()) return null
        val latestWorkout = workouts.maxByOrNull { it.startTimeLocal } ?: return null
        val current = getLatestAssessment()
        val workoutStale = current == null ||
            current.dataEndDate == null ||
            current.dataEndDate.toLocalDate().isBefore(latestWorkout.startTimeLocal.toLocalDate())
        val refreshStale = lastRefreshAt != null &&
            (current == null || current.generatedAt.isBefore(lastRefreshAt))
        if (!workoutStale && !refreshStale) return null
        return regenerateAssessment()
    }
}
