package com.lqborges.garminpacecharts.domain

import com.lqborges.garminpacecharts.TrendDirection
import com.lqborges.garminpacecharts.domain.model.HealthAssessment
import com.lqborges.garminpacecharts.domain.model.HealthMetricDisplay
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
        val priorDay = now.toLocalDate().minusDays(2)

        val vo2 = latestMetric(metrics, "VO2_MAX")
        val vo2Prior = metricOnDate(metrics, "VO2_MAX", lastCompleteDay)
            ?: metricOnDate(metrics, "VO2_MAX", priorDay)
        val restingHr = latestMetric(metrics, "RESTING_HR")
        val restingHrPrior = metricOnDate(metrics, "RESTING_HR", lastCompleteDay)
        val weeklyRhrRank = WellnessStatsBuilder.weeklyValueRank(metrics, "RESTING_HR", now, lowerIsBetter = true)

        val sleepScoreLastNight = metricOnDate(metrics, "SLEEP_SCORE", lastNight)
            ?: metricOnDate(metrics, "SLEEP_SCORE", lastCompleteDay)
        val sleepScorePriorNight = metricOnDate(metrics, "SLEEP_SCORE", lastCompleteDay)
            .takeIf { sleepScoreLastNight != null }
        val sleepDurationLastNight = metricOnDate(metrics, "SLEEP_DURATION", lastNight)
            ?: metricOnDate(metrics, "SLEEP_DURATION", lastCompleteDay)
        val sleepDurationPriorNight = metricOnDate(metrics, "SLEEP_DURATION", lastCompleteDay)
        val sleepScoreAvg = averageMetric(metrics, "SLEEP_SCORE", days = 7, now)
        val sleepScoreAvgPrior = previousAverageMetric(metrics, "SLEEP_SCORE", days = 7, now)
        val sleepDurationAvg = averageMetric(metrics, "SLEEP_DURATION", days = 7, now)
        val sleepDurationAvgPrior = previousAverageMetric(metrics, "SLEEP_DURATION", days = 7, now)

        val stressLastDay = metricOnDate(metrics, "STRESS", lastCompleteDay)
            ?: metricOnDate(metrics, "STRESS", lastNight)
        val stressPriorDay = metricOnDate(metrics, "STRESS", priorDay)
        val stressAvg = averageMetric(metrics, "STRESS", days = 7, now)
        val stressAvgPrior = previousAverageMetric(metrics, "STRESS", days = 7, now)

        val readiness = latestMetric(metrics, "TRAINING_READINESS")
            ?: metricOnDate(metrics, "TRAINING_READINESS", lastNight)
        val readinessPrior = metricOnDate(metrics, "TRAINING_READINESS", lastCompleteDay)
        val endurance = latestMetric(metrics, "ENDURANCE_SCORE")
        val endurancePrior = metricOnDate(metrics, "ENDURANCE_SCORE", lastCompleteDay)

        val strengths = mutableListOf<String>()
        val concerns = mutableListOf<String>()
        val recommendations = mutableListOf<String>()

        if (fourWeekCount >= 6) strengths.add("Running consistency")
        if (runningTrend == TrendDirection.IMPROVING) strengths.add("Progression A pace improving")
        if (vo2 != null && vo2 >= 40) strengths.add("Strong VO2 max (${WellnessMetricParser.formatVo2(vo2)})")
        val sleepScoreForSignals = sleepScoreLastNight ?: sleepScoreAvg
        if (sleepScoreForSignals != null && sleepScoreForSignals >= 75) strengths.add("Good recent sleep scores")
        weeklyRhrRank?.let { if (it.rank <= 3) strengths.add("Elite weekly resting HR") }

        if (fourWeekCount < 4) concerns.add("Low Progression A frequency")
        if (sleepScoreForSignals != null && sleepScoreForSignals < 65) concerns.add("Sleep consistency")
        if (readiness != null && readiness < 50) concerns.add("Low training readiness")
        val stressForSignals = stressLastDay ?: stressAvg
        if (stressForSignals != null && stressForSignals > 35) concerns.add("Elevated stress")

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

        val sleepCoachComments = SleepCoach.comments(
            sleepScoreLastNight = sleepScoreLastNight,
            sleepDurationLastNight = sleepDurationLastNight,
            sleepScoreAvg = sleepScoreAvg,
            sleepDurationAvg = sleepDurationAvg,
            sleepScorePriorNight = sleepScorePriorNight,
        )

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
                metrics = buildList {
                    buildMetric("VO2 max", vo2, vo2Prior, { WellnessMetricParser.formatVo2(it) })?.let(::add)
                    buildMetric("Resting HR", restingHr, restingHrPrior, { "${it.toInt()} bpm" }, lowerIsBetter = true)?.let(::add)
                    weeklyRhrRank?.let { rank ->
                        add(
                            HealthMetricDisplay(
                                label = "Resting HR (week avg)",
                                value = WellnessStatsBuilder.formatWeeklyRhrRank(rank),
                                progress = (1f - (rank.rank.toFloat() / rank.totalWeeks.coerceAtLeast(1))).coerceIn(0.1f, 1f),
                                lowerIsBetter = true,
                            ),
                        )
                    }
                    if (isEmpty()) {
                        add(HealthMetricDisplay(label = "Cardio", value = "no data"))
                    }
                },
            ),
            HealthSection(
                title = "Sleep",
                metrics = buildList {
                    buildMetric(
                        "Sleep score (last night)",
                        sleepScoreLastNight,
                        sleepScorePriorNight,
                        { it.toInt().toString() },
                        progress = sleepScoreLastNight?.let { (it / 100.0).toFloat() },
                    )?.let(::add)
                    buildMetric(
                        "Sleep score (7d avg)",
                        sleepScoreAvg,
                        sleepScoreAvgPrior,
                        { it.toInt().toString() },
                        progress = sleepScoreAvg?.let { (it / 100.0).toFloat() },
                    )?.let(::add)
                    buildMetric(
                        "Sleep duration (last night)",
                        sleepDurationLastNight,
                        sleepDurationPriorNight,
                        { "%.1f h".format(it) },
                        lowerIsBetter = false,
                    )?.let(::add)
                    buildMetric(
                        "Sleep duration (7d avg)",
                        sleepDurationAvg,
                        sleepDurationAvgPrior,
                        { "%.1f h".format(it) },
                    )?.let(::add)
                    if (isEmpty()) {
                        add(HealthMetricDisplay(label = "Sleep", value = "no data"))
                    }
                },
                coachComments = sleepCoachComments,
            ),
            HealthSection(
                title = "Stress / Recovery",
                metrics = buildList {
                    buildMetric("Stress (last day)", stressLastDay, stressPriorDay, { it.toInt().toString() }, lowerIsBetter = true)?.let(::add)
                    buildMetric("Stress (7d avg)", stressAvg, stressAvgPrior, { it.toInt().toString() }, lowerIsBetter = true)?.let(::add)
                    if (isEmpty()) {
                        add(HealthMetricDisplay(label = "Stress", value = "no data"))
                    }
                },
            ),
            HealthSection(
                title = "Training Readiness",
                metrics = buildList {
                    buildMetric("Readiness", readiness, readinessPrior, { it.toInt().toString() }, progress = readiness?.let { (it / 100.0).toFloat() })?.let(::add)
                        ?: add(HealthMetricDisplay(label = "Readiness", value = "no data"))
                },
            ),
            HealthSection(
                title = "Endurance",
                metrics = buildList {
                    buildMetric("Endurance score", endurance, endurancePrior, { it.toInt().toString() }, progress = endurance?.let { (it / 100.0).toFloat() })?.let(::add)
                        ?: add(HealthMetricDisplay(label = "Endurance", value = "no data"))
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
            section.metrics.forEach { metric ->
                val change = metric.percentChange?.let { MetricChange.formatPercent(it) } ?: "n/a"
                appendLine("- ${metric.label}: ${metric.value} (prior: ${metric.priorValue ?: "n/a"}, $change)")
            }
            section.lines.forEach { appendLine("- $it") }
            section.coachComments.forEach { appendLine("- Coach: $it") }
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

    private fun buildMetric(
        label: String,
        current: Double?,
        prior: Double?,
        format: (Double) -> String,
        lowerIsBetter: Boolean = false,
        progress: Float? = null,
    ): HealthMetricDisplay? {
        if (current == null) return null
        return HealthMetricDisplay(
            label = label,
            value = format(current),
            priorValue = prior?.let(format),
            percentChange = prior?.let { MetricChange.percentChange(current, it) },
            lowerIsBetter = lowerIsBetter,
            progress = progress,
        )
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

    private fun previousAverageMetric(
        metrics: List<HealthMetricPoint>,
        type: String,
        days: Long,
        now: LocalDateTime,
    ): Double? {
        val end = now.toLocalDate().minusDays(days)
        val start = end.minusDays(days)
        val values = metrics.filter {
            val day = it.date.toLocalDate()
            it.metricType == type && !day.isBefore(start) && day.isBefore(end)
        }.map { it.value }
        if (values.isEmpty()) return null
        return values.average()
    }
}