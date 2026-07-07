package com.lqborges.garminpacecharts.domain

import com.lqborges.garminpacecharts.TrendDirection
import com.lqborges.garminpacecharts.domain.model.HealthAssessment
import com.lqborges.garminpacecharts.domain.model.HealthMetricPoint
import com.lqborges.garminpacecharts.domain.model.HealthSection
import com.lqborges.garminpacecharts.domain.model.Workout
import java.time.Instant
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

        val vo2 = latestMetric(metrics, "VO2_MAX")
        val restingHr = latestMetric(metrics, "RESTING_HR")
        val sleepScore = averageMetric(metrics, "SLEEP_SCORE", days = 7, now)
        val sleepDuration = averageMetric(metrics, "SLEEP_DURATION", days = 7, now)
        val steps = averageMetric(metrics, "STEPS", days = 7, now)
        val stress = averageMetric(metrics, "STRESS", days = 7, now)
        val readiness = latestMetric(metrics, "TRAINING_READINESS")
        val endurance = latestMetric(metrics, "ENDURANCE_SCORE")

        val strengths = mutableListOf<String>()
        val concerns = mutableListOf<String>()
        val recommendations = mutableListOf<String>()

        if (fourWeekCount >= 6) strengths.add("Running consistency")
        if (runningTrend == TrendDirection.IMPROVING) strengths.add("Progression A pace improving")
        if (vo2 != null && vo2 >= 40) strengths.add("Strong VO2 max (${vo2.toInt()})")
        if (sleepScore != null && sleepScore >= 75) strengths.add("Good recent sleep scores")

        if (fourWeekCount < 4) concerns.add("Low Progression A frequency")
        if (sleepScore != null && sleepScore < 65) concerns.add("Sleep consistency")
        if (readiness != null && readiness < 50) concerns.add("Low training readiness")
        if (stress != null && stress > 35) concerns.add("Elevated stress")

        recommendations += "Keep Progression A twice weekly."
        if (readiness != null && readiness < 50) {
            recommendations += "Avoid hard workouts when readiness < 50."
        }
        if (sleepScore != null && sleepScore < 75) {
            recommendations += "Target sleep score > 75 for 5 nights/week."
        }
        if (latestPace != null && runningTrend == TrendDirection.IMPROVING) {
            recommendations += "Maintain current progression; latest pace ${PaceFormatter.toDisplay(latestPace)} min/km."
        }
        if (steps != null && steps < 7000) {
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
                    steps?.let { add("Avg steps (7d): ${it.toInt()}") } ?: add("Steps: no data")
                },
            ),
            HealthSection(
                title = "Sleep",
                lines = buildList {
                    sleepScore?.let { add("Sleep score (7d avg): ${it.toInt()}") } ?: add("Sleep score: no data")
                    sleepDuration?.let { add("Sleep duration (7d avg): ${"%.1f".format(it)} h") }
                        ?: add("Sleep duration: no data")
                },
            ),
            HealthSection(
                title = "Stress / Recovery",
                lines = buildList {
                    stress?.let { add("Stress (7d avg): ${it.toInt()}") } ?: add("Stress: no data")
                },
            ),
            HealthSection(
                title = "Training Readiness",
                lines = buildList {
                    readiness?.let { add("Latest readiness: ${it.toInt()}") } ?: add("Readiness: no data")
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
