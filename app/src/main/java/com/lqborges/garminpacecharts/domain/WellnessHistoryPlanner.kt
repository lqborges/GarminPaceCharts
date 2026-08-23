package com.lqborges.garminpacecharts.domain

import java.time.LocalDate

/**
 * Date windows for resting-HR history used by weekly ranking.
 * Keeps the recent daily-wellness lookback separate from the multi-year backfill.
 */
object WellnessHistoryPlanner {
    data class DateRange(val start: LocalDate, val end: LocalDate)

    fun backfillRange(
        fetchEnd: LocalDate,
        oldestStored: LocalDate?,
        wellnessLookbackDays: Long,
        historyDays: Long,
    ): DateRange? {
        val targetStart = fetchEnd.minusDays(historyDays)
        val wellnessStart = fetchEnd.minusDays(wellnessLookbackDays)
        val backfillEnd = oldestStored?.minusDays(1) ?: wellnessStart.minusDays(1)
        if (backfillEnd.isBefore(targetStart)) return null
        return DateRange(targetStart, backfillEnd)
    }

    /** Walk [range] backward in inclusive chunks of [chunkDays]. */
    fun chunks(range: DateRange, chunkDays: Long): List<DateRange> {
        require(chunkDays > 0)
        val out = mutableListOf<DateRange>()
        var chunkEnd = range.end
        while (!chunkEnd.isBefore(range.start)) {
            val chunkStart = maxOf(range.start, chunkEnd.minusDays(chunkDays - 1))
            out.add(DateRange(chunkStart, chunkEnd))
            chunkEnd = chunkStart.minusDays(1)
        }
        return out
    }

    fun contains(range: DateRange, date: LocalDate): Boolean =
        !date.isBefore(range.start) && !date.isAfter(range.end)
}
