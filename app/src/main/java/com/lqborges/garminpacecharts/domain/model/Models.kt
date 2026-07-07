package com.lqborges.garminpacecharts.domain.model

import com.lqborges.garminpacecharts.PaceSource
import com.lqborges.garminpacecharts.TrendDirection
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

data class Workout(
    val id: Long = 0,
    val activityId: Long?,
    val activityName: String,
    val startTimeLocal: LocalDateTime,
    val paceMinPerKm: Double,
    val paceSource: PaceSource,
    val distanceMeters: Double? = null,
    val durationSeconds: Long? = null,
    val splitDistanceMeters: Double? = null,
    val splitDurationSeconds: Long? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
    val rawGarminJson: String? = null,
)

data class ImportRow(
    val date: String,
    val pace: Double,
    val activityId: Long?,
    val name: String,
)

data class ImportResult(
    val imported: Int,
    val duplicatesSkipped: Int,
    val invalidRows: List<String>,
    val totalStored: Int,
)

@Serializable
data class ExportWorkoutRow(
    val date: String,
    val pace: Double,
    val activity_id: Long?,
    val name: String,
)

data class RefreshSummary(
    val status: com.lqborges.garminpacecharts.RefreshStatus,
    val lastLocalWorkoutDate: LocalDateTime?,
    val fetchStartDate: String?,
    val fetchEndDate: String?,
    val activitiesFetched: Int,
    val progressionAFound: Int,
    val newWorkoutsAdded: Int,
    val duplicatesSkipped: Int,
    val totalStored: Int,
    val latestWorkoutDate: LocalDateTime?,
    val skippedActivities: List<String> = emptyList(),
    val errorMessage: String? = null,
)

data class WeekBucket(
    val year: Int,
    val week: Int,
    val label: String,
    val month: Int,
    val averagePace: Double,
    val workouts: List<Workout>,
)

enum class AxisMarkerType {
    MONTH,
    YEAR,
}

data class ChartAxisMarker(
    val type: AxisMarkerType,
    val weekIndex: Int,
    val label: String,
)

data class ChartMonthSpan(
    val startWeekIndex: Int,
    val endWeekIndex: Int,
    val month: Int,
    val year: Int,
    val label: String,
)

data class ChartData(
    val title: String,
    val weeks: List<WeekBucket>,
    val showPointLabels: Boolean,
)

data class WeeklyPaceRank(
    val pace: Double,
    val rank: Int,
    val totalWeeks: Int,
) {
    val topPercent: Int
        get() = ((rank.toDouble() / totalWeeks.coerceAtLeast(1)) * 100).toInt().coerceIn(1, 100)
}

data class DashboardStats(
    val totalWorkouts: Int,
    val latestWorkoutDate: LocalDateTime?,
    val latestPace: Double?,
    val fourWeekTrend: TrendDirection,
    val fourWeekAveragePace: Double?,
    val consecutiveWeekStreak: Int,
    val currentWeekAveragePace: Double?,
    val weeklyPaceRank: WeeklyPaceRank?,
    val garminConnected: Boolean,
    val lastRefreshAt: Instant?,
)

data class HealthAssessment(
    val id: Long = 0,
    val generatedAt: Instant = Instant.now(),
    val dataStartDate: LocalDateTime?,
    val dataEndDate: LocalDateTime?,
    val overallStatus: String,
    val summary: String,
    val strengths: List<String>,
    val concerns: List<String>,
    val recommendations: List<String>,
    val confidence: String,
    val sourceVersion: String = "1.0",
    val sections: List<HealthSection> = emptyList(),
    val dataQualityNotes: List<String> = emptyList(),
)

@Serializable
data class HealthSection(
    val title: String,
    val lines: List<String>,
)

data class HealthMetricPoint(
    val metricType: String,
    val date: LocalDateTime,
    val value: Double,
    val unit: String,
)

fun LocalDateTime.toEpochMillis(zoneId: ZoneId = ZoneId.systemDefault()): Long =
    atZone(zoneId).toInstant().toEpochMilli()

fun epochMillisToLocalDateTime(
    millis: Long,
    zoneId: ZoneId = ZoneId.systemDefault(),
): LocalDateTime = Instant.ofEpochMilli(millis).atZone(zoneId).toLocalDateTime()
