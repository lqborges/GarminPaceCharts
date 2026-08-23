package com.lqborges.garminpacecharts.data.repository

import com.lqborges.garminpacecharts.PaceSource
import com.lqborges.garminpacecharts.RefreshStatus
import com.lqborges.garminpacecharts.data.garmin.GarminApiClient
import com.lqborges.garminpacecharts.data.garmin.GarminApiException
import com.lqborges.garminpacecharts.data.local.AppDatabase
import com.lqborges.garminpacecharts.data.local.PreferencesManager
import com.lqborges.garminpacecharts.data.local.entity.HealthMetricSnapshotEntity
import com.lqborges.garminpacecharts.data.local.entity.RefreshRunEntity
import com.lqborges.garminpacecharts.domain.GarminActivityParser
import com.lqborges.garminpacecharts.domain.HealthAssessmentEngine
import com.lqborges.garminpacecharts.domain.PaceExtractor
import com.lqborges.garminpacecharts.domain.DailyMetricValue
import com.lqborges.garminpacecharts.domain.WellnessHistoryPlanner
import com.lqborges.garminpacecharts.domain.WellnessMetricParser
import com.lqborges.garminpacecharts.domain.model.RefreshSummary
import com.lqborges.garminpacecharts.domain.model.Workout
import com.lqborges.garminpacecharts.domain.model.toEpochMillis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class RefreshRepository(
    private val database: AppDatabase,
    private val workoutRepository: WorkoutRepository,
    private val healthRepository: HealthRepository,
    private val garminApiClient: GarminApiClient,
    private val preferencesManager: PreferencesManager,
) {
    private val refreshRunDao = database.refreshRunDao()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun refresh(activityFilter: String = PaceExtractor.DEFAULT_ACTIVITY_FILTER): RefreshSummary =
        withContext(Dispatchers.IO) {
            val startedAt = System.currentTimeMillis()
            val existing = workoutRepository.getWorkouts()
            val lastDate = existing.maxOfOrNull { it.startTimeLocal }
            val fetchStart = (lastDate?.toLocalDate()?.minusDays(1) ?: LocalDate.now().minusDays(120))
            val fetchEnd = LocalDate.now()

            if (!garminApiClient.isConfigured()) {
                return@withContext saveFailedRun(
                    startedAt = startedAt,
                    fetchStart = fetchStart,
                    fetchEnd = fetchEnd,
                    message = "Garmin tokens not configured. Import tokens in Settings.",
                    type = "NOT_CONFIGURED",
                )
            }

            try {
                val activities = garminApiClient.fetchActivitiesByDate(fetchStart, fetchEnd)
                val existingIds = existing.mapNotNull { it.activityId }.toSet()
                val skipped = mutableListOf<String>()
                var progressionFound = 0
                var duplicates = 0
                val newWorkouts = mutableListOf<Workout>()

                activities.forEach { activity ->
                    val name = activity["activityName"]?.jsonPrimitive?.contentOrNull
                    if (!PaceExtractor.matchesActivityName(name, activityFilter)) return@forEach
                    progressionFound++

                    val activityId = GarminActivityParser.parseActivityId(activity)
                    if (activityId != null && activityId in existingIds) {
                        duplicates++
                        skipped.add("$name ($activityId): duplicate")
                        return@forEach
                    }

                    val start = GarminActivityParser.parseStartTime(activity)
                    if (start == null) {
                        skipped.add(
                            "$name (${activityId ?: "?"}): invalid date " +
                                "(${GarminActivityParser.formatStartFields(activity)})",
                        )
                        return@forEach
                    }

                    var detail = activity
                    var extraction = PaceExtractor.extractFromActivity(detail)
                    if (extraction == null && activityId != null) {
                        detail = garminApiClient.fetchActivity(activityId)
                        extraction = PaceExtractor.extractFromActivity(detail)
                    }

                    if (extraction == null) {
                        skipped.add("$name: no valid split pace")
                        return@forEach
                    }

                    val now = Instant.now()
                    newWorkouts.add(
                        Workout(
                            activityId = activityId,
                            activityName = name.orEmpty(),
                            startTimeLocal = start,
                            paceMinPerKm = extraction.paceMinPerKm,
                            paceSource = extraction.paceSource,
                            splitDistanceMeters = extraction.splitDistanceMeters,
                            splitDurationSeconds = extraction.splitDurationSeconds,
                            createdAt = now,
                            updatedAt = now,
                            rawGarminJson = detail.toString(),
                        ),
                    )
                }

                if (newWorkouts.isNotEmpty()) {
                    workoutRepository.upsertWorkouts(newWorkouts)
                }

                val metrics = fetchWellnessMetrics(fetchEnd)
                if (metrics.isNotEmpty()) {
                    // Persist recent wellness first so RHR backfill can see what's already stored.
                    replaceHealthMetrics(metrics)
                }
                val rhrHistory = fetchHistoricalRestingHr(fetchEnd)
                if (rhrHistory.isNotEmpty()) {
                    replaceHealthMetrics(rhrHistory)
                }
                healthRepository.regenerateAssessment()

                val total = workoutRepository.getWorkouts()
                val latest = total.maxByOrNull { it.startTimeLocal }
                preferencesManager.setLastRefreshAt(System.currentTimeMillis())

                val summary = RefreshSummary(
                    status = RefreshStatus.SUCCESS,
                    lastLocalWorkoutDate = lastDate,
                    fetchStartDate = fetchStart.toString(),
                    fetchEndDate = fetchEnd.toString(),
                    activitiesFetched = activities.size,
                    progressionAFound = progressionFound,
                    newWorkoutsAdded = newWorkouts.size,
                    duplicatesSkipped = duplicates,
                    totalStored = total.size,
                    latestWorkoutDate = latest?.startTimeLocal,
                    skippedActivities = skipped,
                )

                refreshRunDao.insert(
                    RefreshRunEntity(
                        startedAtMillis = startedAt,
                        finishedAtMillis = System.currentTimeMillis(),
                        status = summary.status.name,
                        fetchStartDate = summary.fetchStartDate,
                        fetchEndDate = summary.fetchEndDate,
                        activitiesFetched = summary.activitiesFetched,
                        progressionAFound = summary.progressionAFound,
                        newWorkoutsAdded = summary.newWorkoutsAdded,
                        duplicatesSkipped = summary.duplicatesSkipped,
                        activitiesSkipped = skipped.size,
                        errorMessage = null,
                        errorType = null,
                    ),
                )
                summary
            } catch (e: GarminApiException) {
                saveFailedRun(startedAt, fetchStart, fetchEnd, e.message ?: "Garmin fetch failed", e.type)
            } catch (e: Exception) {
                saveFailedRun(startedAt, fetchStart, fetchEnd, e.message ?: "Refresh failed", "UNKNOWN")
            }
        }

    private suspend fun replaceHealthMetrics(metrics: List<HealthMetricSnapshotEntity>) {
        val dao = database.healthMetricDao()
        metrics.groupBy { it.metricType }.forEach { (type, rows) ->
            val from = rows.minOf { it.dateMillis }
            val to = rows.maxOf { it.dateMillis }
            dao.deleteTypeInRange(type, from, to)
        }
        dao.upsertAll(metrics)
    }

    private suspend fun fetchWellnessMetrics(end: LocalDate): List<HealthMetricSnapshotEntity> {
        val metrics = mutableListOf<HealthMetricSnapshotEntity>()
        val start = end.minusDays(WELLNESS_LOOKBACK_DAYS)
        var date = end
        while (!date.isBefore(start)) {
            metrics.addAll(fetchDailyWellness(date))
            date = date.minusDays(1)
        }
        return metrics
    }

    /**
     * Backfill resting HR so weekly ranking can use all stored history, not the
     * 14-day daily-wellness window. Garmin's userstats series often caps near
     * 28 days per request, so we walk backward in that size and ignore points
     * outside the requested chunk (the API sometimes returns the latest window).
     */
    private suspend fun fetchHistoricalRestingHr(end: LocalDate): List<HealthMetricSnapshotEntity> {
        val oldestMillis = database.healthMetricDao().oldestDateMillis("RESTING_HR")
        val oldestStored = oldestMillis?.let {
            Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
        }
        val range = WellnessHistoryPlanner.backfillRange(
            fetchEnd = end,
            oldestStored = oldestStored,
            wellnessLookbackDays = WELLNESS_LOOKBACK_DAYS,
            historyDays = RHR_HISTORY_DAYS,
        ) ?: return emptyList()

        val metrics = mutableListOf<HealthMetricSnapshotEntity>()
        var emptyStreak = 0
        for (chunk in WellnessHistoryPlanner.chunks(range, RHR_BULK_CHUNK_DAYS)) {
            val points = fetchRestingHrChunk(chunk)
            if (points.isEmpty()) {
                emptyStreak++
                if (emptyStreak >= EMPTY_CHUNK_STOP) break
            } else {
                emptyStreak = 0
                points.forEach { point ->
                    metrics.add(
                        metric(
                            "RESTING_HR",
                            point.date.atStartOfDay().toEpochMillis(),
                            point.value,
                            "bpm",
                        ),
                    )
                }
            }
        }
        return metrics
    }

    private suspend fun fetchRestingHrChunk(
        chunk: WellnessHistoryPlanner.DateRange,
    ): List<DailyMetricValue> {
        val bulk = garminApiClient.fetchRestingHrRange(chunk.start, chunk.end)
        val parsed = WellnessMetricParser.restingHrSeriesFromUserStats(bulk)
            .filter { WellnessHistoryPlanner.contains(chunk, it.date) }
        if (parsed.isNotEmpty()) return parsed

        val daily = mutableListOf<DailyMetricValue>()
        var date = chunk.end
        while (!date.isBefore(chunk.start)) {
            garminApiClient.fetchDailySummary(date)
                ?.get("restingHeartRate")
                ?.jsonPrimitive
                ?.intOrNull
                ?.toDouble()
                ?.takeIf { it > 0.0 }
                ?.let { daily.add(DailyMetricValue(date, it)) }
            date = date.minusDays(1)
        }
        return daily
    }

    private suspend fun fetchDailyWellness(date: LocalDate): List<HealthMetricSnapshotEntity> {
        val metrics = mutableListOf<HealthMetricSnapshotEntity>()
        val millis = date.atStartOfDay().toEpochMillis()
        var vo2Added = false
        garminApiClient.fetchDailySummary(date)?.let { summary ->
            summary["restingHeartRate"]?.jsonPrimitive?.intOrNull?.toDouble()?.let {
                metrics.add(metric("RESTING_HR", millis, it, "bpm"))
            }
            summary["totalSteps"]?.jsonPrimitive?.intOrNull?.toDouble()?.let {
                metrics.add(metric("STEPS", millis, it, "steps"))
            }
            summary["averageStressLevel"]?.jsonPrimitive?.intOrNull?.toDouble()?.let {
                metrics.add(metric("STRESS", millis, it, "score"))
            }
            summary["vo2MaxPrecise"]?.jsonPrimitive?.doubleOrNull?.let {
                metrics.add(metric("VO2_MAX", millis, it, "ml/kg/min"))
                vo2Added = true
            } ?: summary["vo2Max"]?.jsonPrimitive?.doubleOrNull?.let {
                metrics.add(metric("VO2_MAX", millis, it, "ml/kg/min"))
                vo2Added = true
            }
        }
        if (!vo2Added) {
            WellnessMetricParser.vo2FromMaxMet(garminApiClient.fetchMaxMetDaily(date))?.let {
                metrics.add(metric("VO2_MAX", millis, it, "ml/kg/min"))
            }
        }
        garminApiClient.fetchSleepData(date)?.let { sleep ->
            val daily = sleep["dailySleepDTO"]?.jsonObject
            daily?.get("sleepScores")?.jsonObject?.get("overall")?.jsonObject
                ?.get("value")?.jsonPrimitive?.intOrNull?.toDouble()?.let {
                    metrics.add(metric("SLEEP_SCORE", millis, it, "score"))
                }
            daily?.get("sleepTimeSeconds")?.jsonPrimitive?.longOrNull?.let { secs ->
                metrics.add(metric("SLEEP_DURATION", millis, secs / 3600.0, "hours"))
            }
        }
        parseReadinessScore(garminApiClient.fetchTrainingReadiness(date))?.let {
            metrics.add(metric("TRAINING_READINESS", millis, it, "score"))
        }
        garminApiClient.fetchEnduranceScore(date)?.let { endurance ->
            endurance["overallScore"]?.jsonPrimitive?.doubleOrNull?.let {
                metrics.add(metric("ENDURANCE_SCORE", millis, it, "score"))
            }
        }
        return metrics
    }

    private fun parseReadinessScore(element: JsonElement?): Double? {
        when (element) {
            null -> return null
            is JsonArray -> {
                val morning = element.firstOrNull {
                    (it as? JsonObject)?.get("inputContext")?.jsonPrimitive?.contentOrNull ==
                        "AFTER_WAKEUP_RESET"
                } as? JsonObject
                val entry = morning ?: element.firstOrNull() as? JsonObject ?: return null
                return entry["score"]?.jsonPrimitive?.doubleOrNull
                    ?: entry["score"]?.jsonPrimitive?.intOrNull?.toDouble()
            }
            is JsonObject -> {
                return element["score"]?.jsonPrimitive?.doubleOrNull
                    ?: element["score"]?.jsonPrimitive?.intOrNull?.toDouble()
            }
            else -> return null
        }
    }

    companion object {
        private const val WELLNESS_LOOKBACK_DAYS = 14L
        /** Target span of resting-HR history used for weekly ranking (~5 years). */
        private const val RHR_HISTORY_DAYS = 365L * 5
        /** Garmin userstats RHR series commonly returns at most ~28 days per call. */
        private const val RHR_BULK_CHUNK_DAYS = 28L
        /** Stop walking older chunks after this many consecutive empty windows. */
        private const val EMPTY_CHUNK_STOP = 3
    }

    private fun metric(type: String, millis: Long, value: Double, unit: String) =
        HealthMetricSnapshotEntity(
            source = "GARMIN_API",
            metricType = type,
            dateMillis = millis,
            value = value,
            unit = unit,
            confidence = "medium",
        )

    private suspend fun saveFailedRun(
        startedAt: Long,
        fetchStart: LocalDate,
        fetchEnd: LocalDate,
        message: String,
        type: String,
    ): RefreshSummary {
        val existing = workoutRepository.getWorkouts()
        val summary = RefreshSummary(
            status = RefreshStatus.FAILED,
            lastLocalWorkoutDate = existing.maxOfOrNull { it.startTimeLocal },
            fetchStartDate = fetchStart.toString(),
            fetchEndDate = fetchEnd.toString(),
            activitiesFetched = 0,
            progressionAFound = 0,
            newWorkoutsAdded = 0,
            duplicatesSkipped = 0,
            totalStored = existing.size,
            latestWorkoutDate = existing.maxOfOrNull { it.startTimeLocal },
            errorMessage = message,
        )
        refreshRunDao.insert(
            RefreshRunEntity(
                startedAtMillis = startedAt,
                finishedAtMillis = System.currentTimeMillis(),
                status = RefreshStatus.FAILED.name,
                fetchStartDate = fetchStart.toString(),
                fetchEndDate = fetchEnd.toString(),
                activitiesFetched = 0,
                progressionAFound = 0,
                newWorkoutsAdded = 0,
                duplicatesSkipped = 0,
                activitiesSkipped = 0,
                errorMessage = message,
                errorType = type,
            ),
        )
        return summary
    }
}
