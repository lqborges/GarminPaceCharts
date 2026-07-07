package com.lqborges.garminpacecharts.domain

import com.lqborges.garminpacecharts.ChartRange
import com.lqborges.garminpacecharts.domain.model.ChartData
import com.lqborges.garminpacecharts.domain.model.WeekBucket
import com.lqborges.garminpacecharts.domain.model.Workout
import java.time.LocalDateTime
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

object ChartDataBuilder {
    val MONTH_COLORS = mapOf(
        1 to 0xFF1F77B4, 2 to 0xFF1F77B4,
        3 to 0xFF2CA02C, 4 to 0xFF2CA02C,
        5 to 0xFFFF7F0E, 6 to 0xFFFF7F0E,
        7 to 0xFFD62728, 8 to 0xFFD62728,
        9 to 0xFF9467BD, 10 to 0xFF9467BD,
        11 to 0xFF8C564B, 12 to 0xFF8C564B,
    )

    fun build(workouts: List<Workout>, range: ChartRange, now: LocalDateTime = LocalDateTime.now()): ChartData {
        val today = now.toLocalDate()
        val filtered = when (range) {
            ChartRange.LAST_4_WEEKS -> workouts.filter {
                it.startTimeLocal.toLocalDate() >= today.minusWeeks(4)
            }
            ChartRange.LAST_YEAR -> workouts.filter {
                it.startTimeLocal.toLocalDate() >= today.minusWeeks(52)
            }
            ChartRange.ALL_TIME -> workouts
        }

        val grouped = filtered
            .groupBy { weekKey(it.startTimeLocal) }
            .toSortedMap(compareBy({ it.first }, { it.second }))

        val weeks = grouped.map { (key, weekWorkouts) ->
            val sorted = weekWorkouts.sortedBy { it.startTimeLocal }
            val latest = sorted.last().startTimeLocal
            val avg = sorted.map { it.paceMinPerKm }.average()
            WeekBucket(
                year = key.first,
                week = key.second,
                label = "${latest.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())} ${key.second}",
                month = latest.monthValue,
                averagePace = avg,
                workouts = sorted,
            )
        }

        val title = when (range) {
            ChartRange.LAST_4_WEEKS -> "Progression A — Last 4 Weeks"
            ChartRange.LAST_YEAR -> "Progression A — Last Year"
            ChartRange.ALL_TIME -> "Progression A — All Time"
        }

        return ChartData(
            title = title,
            weeks = weeks,
            showPointLabels = range == ChartRange.LAST_4_WEEKS,
        )
    }

    fun weekKey(date: LocalDateTime): Pair<Int, Int> {
        val week = date.toLocalDate().get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR)
        val year = date.toLocalDate().get(java.time.temporal.IsoFields.WEEK_BASED_YEAR)
        return year to week
    }

    fun calculateOffsets(count: Int): List<Float> {
        if (count <= 1) return listOf(0f)
        val spacing = minOf(0.13f, 0.4f / count)
        return List(count) { index ->
            val start = -spacing * (count - 1) / 2f
            start + spacing * index
        }
    }

    fun fourWeekAveragePace(workouts: List<Workout>, now: LocalDateTime = LocalDateTime.now()): Double? {
        val cutoff = now.toLocalDate().minusWeeks(4)
        val recent = workouts.filter { it.startTimeLocal.toLocalDate() >= cutoff }
        if (recent.isEmpty()) return null
        return recent.map { it.paceMinPerKm }.average()
    }

    fun previousFourWeekAveragePace(workouts: List<Workout>, now: LocalDateTime = LocalDateTime.now()): Double? {
        val today = now.toLocalDate()
        val start = today.minusWeeks(8)
        val end = today.minusWeeks(4)
        val previous = workouts.filter {
            val day = it.startTimeLocal.toLocalDate()
            !day.isBefore(start) && day.isBefore(end)
        }
        if (previous.isEmpty()) return null
        return previous.map { it.paceMinPerKm }.average()
    }
}
