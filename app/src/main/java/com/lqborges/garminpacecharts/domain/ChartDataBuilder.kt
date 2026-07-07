package com.lqborges.garminpacecharts.domain

import com.lqborges.garminpacecharts.ChartRange
import com.lqborges.garminpacecharts.domain.model.AxisMarkerType
import com.lqborges.garminpacecharts.domain.model.ChartAxisMarker
import com.lqborges.garminpacecharts.domain.model.ChartData
import com.lqborges.garminpacecharts.domain.model.ChartMonthSpan
import com.lqborges.garminpacecharts.domain.model.WeekBucket
import com.lqborges.garminpacecharts.domain.model.Workout
import java.time.LocalDateTime
import java.time.Month
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max

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
                label = weekDateLabel(sorted),
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

    /** X-axis label from actual workout dates — never ISO week numbers (they read like calendar dates). */
    fun weekDateLabel(workouts: List<Workout>): String {
        if (workouts.isEmpty()) return ""
        val dates = workouts.map { it.startTimeLocal.toLocalDate() }.sorted()
        val first = dates.first()
        val last = dates.last()
        val monthDay = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
        return when {
            dates.size == 1 || first == last -> monthDay.format(first)
            first.month == last.month -> "${monthDay.format(first)}–${last.dayOfMonth}"
            else -> "${monthDay.format(first)}–${monthDay.format(last)}"
        }
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

    /** Minimum week columns between x-axis date labels; grows as the chart is zoomed out. */
    fun weekLabelStride(weekWidthPx: Float, minLabelSpacingPx: Float): Int =
        max(1, ceil(minLabelSpacingPx / weekWidthPx.coerceAtLeast(1f)).toInt())

    fun shouldShowWeekLabel(weekIndex: Int, weekCount: Int, stride: Int): Boolean =
        weekIndex % stride == 0 || weekIndex == weekCount - 1

    fun buildAxisMarkers(weeks: List<WeekBucket>): List<ChartAxisMarker> {
        if (weeks.isEmpty()) return emptyList()
        val markers = mutableListOf<ChartAxisMarker>()
        weeks.forEachIndexed { index, week ->
            val previous = weeks.getOrNull(index - 1)
            if (previous == null || previous.year != week.year) {
                markers.add(
                    ChartAxisMarker(
                        type = AxisMarkerType.YEAR,
                        weekIndex = index,
                        label = week.year.toString(),
                    ),
                )
            }
            if (previous == null || previous.month != week.month || previous.year != week.year) {
                markers.add(
                    ChartAxisMarker(
                        type = AxisMarkerType.MONTH,
                        weekIndex = index,
                        label = Month.of(week.month).getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                    ),
                )
            }
        }
        return markers
    }

    fun buildMonthSpans(weeks: List<WeekBucket>): List<ChartMonthSpan> {
        if (weeks.isEmpty()) return emptyList()
        val spans = mutableListOf<ChartMonthSpan>()
        var spanStart = 0
        weeks.forEachIndexed { index, week ->
            val previous = weeks.getOrNull(index - 1)
            val monthChanged = previous != null &&
                (previous.month != week.month || previous.year != week.year)
            if (monthChanged) {
                spans += monthSpan(weeks, spanStart, index - 1)
                spanStart = index
            }
        }
        spans += monthSpan(weeks, spanStart, weeks.lastIndex)
        return spans
    }

    private fun monthSpan(weeks: List<WeekBucket>, start: Int, end: Int): ChartMonthSpan {
        val anchor = weeks[start]
        val monthLabel = Month.of(anchor.month).getDisplayName(TextStyle.SHORT, Locale.getDefault())
        return ChartMonthSpan(
            startWeekIndex = start,
            endWeekIndex = end,
            month = anchor.month,
            year = anchor.year,
            label = monthLabel,
        )
    }
}
