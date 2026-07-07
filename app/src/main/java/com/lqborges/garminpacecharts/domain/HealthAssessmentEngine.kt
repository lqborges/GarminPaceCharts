package com.lqborges.garminpacecharts.domain

import com.lqborges.garminpacecharts.TrendDirection
import com.lqborges.garminpacecharts.domain.model.HealthAssessment
import com.lqborges.garminpacecharts.domain.model.HealthMetricPoint
import com.lqborges.garminpacecharts.domain.model.HealthSection
import com.lqborges.garminpacecharts.domain.model.Workout
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.abs

object HealthAssessmentEngine {
    const val DISCLAIMER =
        "This assessment is based on Garmin wearable data and is not medical advice. " +
            "Use it for fitness trend awareness, not diagnosis."

    fun computeTrend(
        latestWindow: List<Double>,
        previousWindow: List<Double>,
        lowerIsBetter: Boolean = true,
    ): TrendDirection {
        if (latestWindow.size < 2 || previousWindow.size < 2) return TrendDirection.INSUFFICIENT_DATA
        val latestAvg = latestWindow.average()
        val previousAvg = previousWindow.average()
        val delta = latestAvg - previousAvg
        if (abs(delta) < 0.05 * previousAvg.coerceAtLeast(1.0)) return TrendDirection.STABLE
        val improving = if (lowerIsBetter) delta < 0 else delta > 0
        return if (improving) TrendDirection.IMPROVING else TrendDirection.DECLINING
    }

    fun computeRunningTrend(workouts: List<Workout>, now: LocalDateTime = LocalDateTime.now()): TrendDirection {
        val today = now.toLocalDate()
        val recent = workouts.filter {
            !it.startTimeLocal.toLocalDate().isBefore(today.minusDays(7))
        }.map { it.paceMinPerKm }
        val previous = workouts.filter {
            val day = it.startTimeLocal.toLocalDate()
            !day.isBefore(today.minusDays(14)) && day.isBefore(today.minusDays(7))
        }.map { it.paceMinPerKm }
        return computeTrend(recent, previous, lowerIsBetter = true)
    }

    fun generate(
        workouts: List<Workout>,
        metrics: List<HealthMetricPoint>,
        now: LocalDateTime = LocalDateTime.now(),
    ): HealthAssessment {
        val dataStart = workouts.minOfOrNull { it.startTimeLocal }
            ?: metrics.minOfOrNull { it.date }
        val dataEnd = workouts.maxOfOrNull { it.startTimeLocal }
            ?: metrics.maxOfOrNull { it.date }

        val runningTrend = computeRunningTrend(workouts, now)
        val fourWeekCutoff = now.toLocalDate().minusWeeks(4)
        val fourWeekCount = workouts.count { it.startTimeLocal.toLocalDate() >= fourWeekCutoff }
        val latestPace = workouts.maxByOrNull { it.startTimeLocal }?.paceMinPerKm

        val lastNight = now.toLocalDate()
        val lastCompleteDay = now.toLocalDate().minusDays(1)

        val vo2 = latestMetric(metrics, "VO2_MAX")
        val restingHr = latestMetric(metrics, "RESTING_HR")
        val sleepScoreLastNight = metricOnDate(metrics, "SLEEP_SCORE", lastNight)
            ?: metricOnDate(metrics, "SLEEP_SCORE", lastCompleteDay)
        val sleepDurationLastNight = metricOnDate(metrics, "SLEEP_DURATION", lastNight)
            ?: metricOnDate(metrics, "SLEEP_DURATION", lastCompleteDay)
        val sleepScoreAvg = averageMetric(metrics, "SLEEP_SCORE", days = 7, now)
        val sleepDurationAvg = averageMetric(metrics, "SLEEP_DURATION", days = 7, now)
        val stepsLastDay = metricOnDate(metrics, "STEPS", lastCompleteDay)
            ?: metricOnDate(metrics, "STEPS", lastNight)
        val stepsAvg = averageMetric(metrics, "STEPS", days = 7, now)
        val stressLastDay = metricOnDate(metrics, "STRESS", lastCompleteDay)
            ?: metricOnDate(metrics, "STRESS", lastNight)
        val stressAvg = averageMetric(metrics, "STRESS", days = 7, now)
        val readiness = latestMetric(metrics, "TRAINING_READINESS")
            ?: metricOnDate(metrics, "TRAINING_READINESS", lastNight)
        val readinessLastNight = metricOnDate(metrics, "TRAINING_READINESS", lastCompleteDay)
        val endurance = latestMetric(metrics, "ENDURANCE_SCORE")

        val strengths = mutableListOf<String>()
        val concerns = mutableListOf<String>()
        val recommendations = mutableListOf<String>()

        if (fourWeekCount >= 6) strengths.add("Running consistency")
        if (runningTrend == TrendDirection.IMPROVING) strengths.add("Progression A pace improving")
        if (vo2 != null && vo2 >= 40) strengths.add("Strong VO2 max (${vo2.toInt()})")
        val sleepScoreForSignals = sleepScoreLastNight ?: sleepScoreAvg
        if (sleepScoreForSignals != null && sleepScoreForSignals >= 75) strengths.add("Good recent sleep scores")

        if (fourWeekCount < 4) concerns.add("Low Progression A frequency")
        if (sleepScoreForSignals != null && sleepScoreForSignals < 65) concerns.add("Sleep consistency")
        if (readiness != null && readiness < 50) concerns.add("Low training readiness")
        val stressForSignals = stressLastDay ?: stressAvg
        if (stressForSignals != null && stressForSignals > 35) {
            concerns.add("Elevated stress")
        }

        recommendations += "Keep Progression A twice weekly."
        if (readiness != null && readiness < 50) {
            recommendations += "Avoid hard workouts when readiness < 50."
        }
        if (sleepScoreForSignals != null && sleepScoreForSignals < 75) {
            recommendations += "Target sleep score > 75 for 5 nights/week."
        }
        if (latestPace != null && runningTrend == TrendDirection.IMPROVING) {
            recommendations += "Maintain current progression; latest pace ${PaceFormatter.toDisplay(latestPace)} min/km."
        }
        val stepsForSignals = stepsLastDay ?: stepsAvg
        if (stepsForSignals != null && stepsForSignals < 7000) {
            recommendations += "Increase daily steps toward 8,000+ on non-running days."
        }

        val overallStatus = when {
            concerns.isEmpty() && strengths.isNotEmpty() -> "Good, improving"
            concerns.size <= 1 -> "Stable"
            else -> "Needs attention"
        }

        val sections = listOf(
            HealthSection(
                title = "Profile",
                lines = buildList {
                    add("Workouts tracked: ${workouts.size}")
                    dataStart?.let { add("Data from: $it") }
                    dataEnd?.let { add("Data through: $it") }
                },
            ),
            HealthSection(
                title = "Cardio",
                lines = buildList {
                    vo2?.let { add("VO2 max: ${it.toInt()}") } ?: add("VO2 max: no data")
                    restingHr?.let { add("Resting HR: ${it.toInt()} bpm") } ?: add("Resting HR: no data")
                },
            ),
            HealthSection(
                title = "Activity",
                lines = buildList {
                    add("Progression A (4w): $fourWeekCount sessions")
                    latestPace?.let { add("Latest pace: ${PaceFormatter.toDisplay(it)} min/km") }
                    stepsLastDay?.let { add("Steps (last day): ${it.toInt()}") }
                    stepsAvg?.let { add("Steps (7d avg): ${it.toInt()}") }
                    if (stepsLastDay == null && stepsAvg == null) add("Steps: no data")
                },
            ),
            HealthSection(
                title = "Sleep",
                lines = buildList {
                    sleepScoreLastNight?.let { add("Sleep score (last night): ${it.toInt()}") }
                    sleepScoreAvg?.let { add("Sleep score (7d avg): ${it.toInt()}") }
                    if (sleepScoreLastNight == null && sleepScoreAvg == null) add("Sleep score: no data")
                    sleepDurationLastNight?.let { add("Sleep duration (last night): ${"%.1f".format(it)} h") }
                    sleepDurationAvg?.let { add("Sleep duration (7d avg): ${"%.1f".format(it)} h") }
                    if (sleepDurationLastNight == null && sleepDurationAvg == null) add("Sleep duration: no data")
                },
            ),
            HealthSection(
                title = "Stress / Recovery",
                lines = buildList {
                    stressLastDay?.let { add("Stress (last day): ${it.toInt()}") }
                    stressAvg?.let { add("Stress (7d avg): ${it.toInt()}") }
                    if (stressLastDay == null && stressAvg == null) add("Stress: no data")
                },
            ),
            HealthSection(
                title = "Training Readiness",
                lines = buildList {
                    readiness?.let { add("Readiness (latest): ${it.toInt()}") }
                    readinessLastNight?.let { add("Readiness (prior day): ${it.toInt()}") }
                    if (readiness == null && readinessLastNight == null) add("Readiness: no data")
                },
            ),
            HealthSection(
                title = "Endurance",
                lines = buildList {
                    endurance?.let { add("Endurance score: ${it.toInt()}") } ?: add("Endurance score: no data")
                },
            ),
        )

        val confidence = when {
            metrics.size >= 5 && workouts.size >= 20 -> "High"
            metrics.isNotEmpty() || workouts.size >= 10 -> "Medium"
            else -> "Low"
        }

        val dataQualityNotes = buildList {
            if (metrics.isEmpty()) add("No Garmin wellness metrics imported yet; assessment uses workout data only.")
            if (workouts.isEmpty()) add("No workouts available.")
            add(DISCLAIMER)
        }

        return HealthAssessment(
            generatedAt = Instant.now(),
            dataStartDate = dataStart,
            dataEndDate = dataEnd,
            overallStatus = overallStatus,
            summary = "Status: $overallStatus\nStrength: ${strengths.firstOrNull() ?: "n/a"}\nConcern: ${concerns.firstOrNull() ?: "none"}",
            strengths = strengths,
            concerns = concerns,
            recommendations = recommendations.take(5),
            confidence = confidence,
            sections = sections,
            dataQualityNotes = dataQualityNotes,
        )
    }

    fun toMarkdown(assessment: HealthAssessment): String = buildString {
        appendLine("# Health Assessment")
        appendLine()
        appendLine("Generated: ${assessment.generatedAt}")
        appendLine("Status: ${assessment.overallStatus}")
        appendLine("Confidence: ${assessment.confidence}")
        appendLine()
        assessment.sections.forEach { section ->
            appendLine("## ${section.title}")
            section.lines.forEach { appendLine("- $it") }
            appendLine()
        }
        appendLine("## Recommendations")
        assessment.recommendations.forEachIndexed { index, rec ->
            appendLine("${index + 1}. $rec")
        }
        appendLine()
        appendLine("## Data quality")
        assessment.dataQualityNotes.forEach { appendLine("- $it") }
    }

    private fun latestMetric(metrics: List<HealthMetricPoint>, type: String): Double? =
        metrics.filter { it.metricType == type }.maxByOrNull { it.date }?.value

    private fun metricOnDate(
        metrics: List<HealthMetricPoint>,
        type: String,
        date: LocalDate,
    ): Double? = metrics
        .filter { it.metricType == type && it.date.toLocalDate() == date }
        .maxByOrNull { it.date }
        ?.value

    private fun averageMetric(
        metrics: List<HealthMetricPoint>,
        type: String,
        days: Long,
        now: LocalDateTime,
    ): Double? {
        val cutoff = now.toLocalDate().minusDays(days)
        val values = metrics.filter {
            it.metricType == type && !it.date.toLocalDate().isBefore(cutoff)
        }.map { it.value }
        if (values.isEmpty()) return null
        return values.average()
    }
}
