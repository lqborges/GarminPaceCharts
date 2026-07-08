package com.lqborges.garminpacecharts.domain

import com.lqborges.garminpacecharts.domain.model.HealthMetricPoint
import com.lqborges.garminpacecharts.domain.model.WeeklyPaceRank
import java.time.LocalDateTime

object WellnessStatsBuilder {
    fun weekKey(date: LocalDateTime) = ChartDataBuilder.weekKey(date)

    fun anchorWeekKey(metrics: List<HealthMetricPoint>, type: String, now: LocalDateTime = LocalDateTime.now()): Pair<Int, Int>? {
        val typed = metrics.filter { it.metricType == type }
        if (typed.isEmpty()) return null
        val current = weekKey(now)
        val weeksWithData = typed.map { weekKey(it.date) }.toSet()
        return if (current in weeksWithData) {
            current
        } else {
            weekKey(typed.maxBy { it.date }.date)
        }
    }

    fun currentWeekAverage(metrics: List<HealthMetricPoint>, type: String, now: LocalDateTime = LocalDateTime.now()): Double? {
        val anchor = anchorWeekKey(metrics, type, now) ?: return null
        val weekValues = metrics.filter {
            it.metricType == type && weekKey(it.date) == anchor
        }.map { it.value }
        if (weekValues.isEmpty()) return null
        return weekValues.average()
    }

    fun weeklyValueRank(
        metrics: List<HealthMetricPoint>,
        type: String,
        now: LocalDateTime = LocalDateTime.now(),
        lowerIsBetter: Boolean,
    ): WeeklyPaceRank? {
        val current = currentWeekAverage(metrics, type, now) ?: return null
        val weeklyAverages = metrics
            .filter { it.metricType == type }
            .groupBy { weekKey(it.date) }
            .values
            .map { week -> week.map { it.value }.average() }
        val totalWeeks = weeklyAverages.size
        if (totalWeeks == 0) return null
        val rank = if (lowerIsBetter) {
            weeklyAverages.count { it < current } + 1
        } else {
            weeklyAverages.count { it > current } + 1
        }
        return WeeklyPaceRank(pace = current, rank = rank, totalWeeks = totalWeeks)
    }

    fun formatWeeklyRhrRank(rank: WeeklyPaceRank): String {
        val value = rank.pace.toInt()
        return when (rank.rank) {
            1 -> "$value bpm — lowest of ${rank.totalWeeks} weeks"
            else -> "$value bpm — ${ChartDataBuilder.ordinal(rank.rank)} of ${rank.totalWeeks} weekly averages (top ${rank.topPercent}%)"
        }
    }
}