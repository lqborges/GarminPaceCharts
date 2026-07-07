package com.lqborges.garminpacecharts.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.lqborges.garminpacecharts.PaceSource
import com.lqborges.garminpacecharts.RefreshStatus

@Entity(
    tableName = "workouts",
    indices = [
        Index(value = ["activityId"], unique = true),
        Index(value = ["startTimeMillis"]),
    ],
)
data class WorkoutEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val activityId: Long?,
    val activityName: String,
    val startTimeMillis: Long,
    val paceMinPerKm: Double,
    val paceSource: String,
    val distanceMeters: Double? = null,
    val durationSeconds: Long? = null,
    val splitDistanceMeters: Double? = null,
    val splitDurationSeconds: Long? = null,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val rawGarminJson: String? = null,
)

@Entity(tableName = "refresh_runs")
data class RefreshRunEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAtMillis: Long,
    val finishedAtMillis: Long?,
    val status: String,
    val fetchStartDate: String?,
    val fetchEndDate: String?,
    val activitiesFetched: Int,
    val progressionAFound: Int,
    val newWorkoutsAdded: Int,
    val duplicatesSkipped: Int,
    val activitiesSkipped: Int,
    val errorMessage: String?,
    val errorType: String?,
)

@Entity(
    tableName = "health_metric_snapshots",
    indices = [Index(value = ["metricType", "dateMillis"])],
)
data class HealthMetricSnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val source: String,
    val metricType: String,
    val dateMillis: Long,
    val value: Double,
    val unit: String,
    val confidence: String,
    val rawJson: String? = null,
)

@Entity(tableName = "health_assessments")
data class HealthAssessmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val generatedAtMillis: Long,
    val dataStartMillis: Long?,
    val dataEndMillis: Long?,
    val overallStatus: String,
    val summary: String,
    val strengthsJson: String,
    val concernsJson: String,
    val recommendationsJson: String,
    val confidence: String,
    val sourceVersion: String,
    val sectionsJson: String,
    val dataQualityNotesJson: String,
)

fun WorkoutEntity.paceSourceEnum(): PaceSource =
    runCatching { PaceSource.valueOf(paceSource) }.getOrDefault(PaceSource.UNKNOWN)

fun RefreshRunEntity.statusEnum(): RefreshStatus =
    runCatching { RefreshStatus.valueOf(status) }.getOrDefault(RefreshStatus.FAILED)
