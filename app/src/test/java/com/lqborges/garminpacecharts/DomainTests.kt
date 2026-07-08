package com.lqborges.garminpacecharts.domain

import com.lqborges.garminpacecharts.PaceSource
import com.lqborges.garminpacecharts.TrendDirection
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.lqborges.garminpacecharts.domain.WellnessMetricParser
import java.time.LocalDateTime

class PaceExtractorTest {
    @Test
    fun matchesActivityName_isCaseInsensitive() {
        assertTrue(PaceExtractor.matchesActivityName("Sheffield - Progression A -"))
        assertFalse(PaceExtractor.matchesActivityName("Easy Run"))
    }

    @Test
    fun isValidPace_usesScriptRange() {
        assertTrue(PaceExtractor.isValidPace(5.5))
        assertFalse(PaceExtractor.isValidPace(3.0))
        assertFalse(PaceExtractor.isValidPace(9.5))
    }

    @Test
    fun extract_prefersIntervalActive() {
        val json = """
            {
              "splitSummaries": [
                {"splitType":"WARMUP","distance":500,"duration":300},
                {"splitType":"INTERVAL_ACTIVE","distance":1000,"duration":360}
              ]
            }
        """.trimIndent()
        val result = PaceExtractor.extractFromActivity(
            kotlinx.serialization.json.Json.parseToJsonElement(json).jsonObject,
        )
        assertNotNull(result)
        assertEquals(PaceSource.INTERVAL_ACTIVE, result!!.paceSource)
        assertEquals(6.0, result.paceMinPerKm, 0.01)
    }

    @Test
    fun extract_usesRwdRunWhenNoIntervalActive() {
        val json = """
            {
              "splitSummaries": [
                {"splitType":"RWD_RUN","distance":1000,"duration":420}
              ]
            }
        """.trimIndent()
        val result = PaceExtractor.extractFromActivity(
            kotlinx.serialization.json.Json.parseToJsonElement(json).jsonObject,
        )
        assertNotNull(result)
        assertEquals(PaceSource.RWD_RUN, result!!.paceSource)
    }

    @Test
    fun extract_rejectsInvalidPace() {
        val json = """
            {
              "splitSummaries": [
                {"splitType":"INTERVAL_ACTIVE","distance":1000,"duration":120}
              ]
            }
        """.trimIndent()
        assertNull(
            PaceExtractor.extractFromActivity(
                kotlinx.serialization.json.Json.parseToJsonElement(json).jsonObject,
            ),
        )
    }
}

class ImportExportTest {
    @Test
    fun parseImportJson_acceptsScriptFormat() {
        val raw = """
            [
              {"date":"2022-02-16T07:45:17","pace":9.42,"activity_id":8305053426,"name":"Sheffield - Progression A -"}
            ]
        """.trimIndent()
        val (rows, invalid) = ImportExport.parseImportJson(raw)
        assertEquals(1, rows.size)
        assertTrue(invalid.isEmpty())
        assertEquals(8305053426L, rows[0].activityId)
    }

    @Test
    fun mergeImports_deduplicatesByActivityId() {
        val existing = listOf(
            com.lqborges.garminpacecharts.domain.model.Workout(
                activityId = 1L,
                activityName = "A",
                startTimeLocal = LocalDateTime.parse("2024-01-01T08:00:00"),
                paceMinPerKm = 6.0,
                paceSource = PaceSource.IMPORTED,
            ),
        )
        val rows = listOf(
            com.lqborges.garminpacecharts.domain.model.ImportRow(
                date = "2024-01-02T08:00:00",
                pace = 5.8,
                activityId = 1L,
                name = "A",
            ),
            com.lqborges.garminpacecharts.domain.model.ImportRow(
                date = "2024-01-03T08:00:00",
                pace = 5.7,
                activityId = 2L,
                name = "B",
            ),
        )
        val (_, result) = ImportExport.mergeImports(existing, rows)
        assertEquals(1, result.imported)
        assertEquals(1, result.duplicatesSkipped)
    }

    @Test
    fun export_matchesScriptFields() {
        val workouts = listOf(
            com.lqborges.garminpacecharts.domain.model.Workout(
                activityId = 99L,
                activityName = "Progression A",
                startTimeLocal = LocalDateTime.parse("2024-06-01T07:30:00"),
                paceMinPerKm = 6.25,
                paceSource = PaceSource.IMPORTED,
            ),
        )
        val exported = ImportExport.exportToJson(workouts)
        assertTrue(exported.contains("\"activity_id\":99"))
        assertTrue(exported.contains("\"pace\":6.25"))
    }
}

class DateParserTest {
    @Test
    fun parse_isoWithT() {
        assertEquals(
            LocalDateTime.of(2026, 6, 9, 8, 3, 12),
            DateParser.parseLocalDateTime("2026-06-09T08:03:12"),
        )
    }

    @Test
    fun parse_garminApiSpaceFormat() {
        assertEquals(
            LocalDateTime.of(2026, 7, 5, 7, 23, 5),
            DateParser.parseLocalDateTime("2026-07-05 07:23:05"),
        )
    }
}

class WellnessMetricParserTest {
    @Test
    fun vo2FromMaxMet_readsGenericVo2MaxPreciseValue() {
        val json = """
            [
              {
                "generic": {
                  "calendarDate": "2026-07-05",
                  "vo2MaxPreciseValue": 49.0,
                  "vo2MaxValue": 49.0
                }
              }
            ]
        """.trimIndent()
        val element = kotlinx.serialization.json.Json.parseToJsonElement(json)
        assertEquals(49.0, WellnessMetricParser.vo2FromMaxMet(element)!!, 0.01)
    }

    @Test
    fun healthAssessment_includesVo2MaxFromMetrics() {
        val metrics = listOf(
            com.lqborges.garminpacecharts.domain.model.HealthMetricPoint(
                metricType = "VO2_MAX",
                date = LocalDateTime.parse("2026-07-05T00:00:00"),
                value = 49.0,
                unit = "ml/kg/min",
            ),
        )
        val assessment = HealthAssessmentEngine.generate(emptyList(), metrics)
        val cardio = assessment.sections.first { it.title == "Cardio" }
        assertTrue(cardio.lines.any { it == "VO2 max: 49" })
    }
}

class ChartDataBuilderTest {
    @Test
    fun build_groupsByIsoWeek() {
        val workouts = listOf(
            workout("2024-06-03T08:00:00", 6.0),
            workout("2024-06-05T08:00:00", 6.2),
        )
        val chart = ChartDataBuilder.build(
            workouts,
            com.lqborges.garminpacecharts.ChartRange.ALL_TIME,
            now = LocalDateTime.parse("2024-06-10T08:00:00"),
        )
        assertEquals(1, chart.weeks.size)
        assertEquals(2, chart.weeks.first().workouts.size)
    }

    @Test
    fun build_lastFourWeeks_includesJulyWorkout() {
        val workouts = listOf(
            workout("2026-06-29T07:30:00", 7.9),
            workout("2026-07-05T07:23:05", 7.88),
        )
        val chart = ChartDataBuilder.build(
            workouts,
            com.lqborges.garminpacecharts.ChartRange.LAST_4_WEEKS,
            now = LocalDateTime.parse("2026-07-07T22:00:00"),
        )
        assertEquals(1, chart.weeks.size)
        assertEquals("Jun 29–Jul 5", chart.weeks.single().label)
        assertEquals(2, chart.weeks.single().workouts.size)
        assertEquals(7, chart.weeks.single().month)
    }

    @Test
    fun build_weekLabelShowsActualWorkoutDates() {
        val workouts = listOf(
            workout("2026-06-29T07:30:00", 7.9),
            workout("2026-07-05T07:23:05", 7.88),
        )
        val chart = ChartDataBuilder.build(
            workouts,
            com.lqborges.garminpacecharts.ChartRange.ALL_TIME,
            now = LocalDateTime.parse("2026-07-07T22:00:00"),
        )
        assertEquals("Jun 29–Jul 5", chart.weeks.single().label)
    }

    @Test
    fun weekDateLabel_singleWorkoutShowsCalendarDate() {
        val workouts = listOf(workout("2026-07-05T07:23:05", 7.88))
        assertEquals("Jul 5", ChartDataBuilder.weekDateLabel(workouts))
    }

    @Test
    fun healthAssessment_includesJulyWorkoutInFourWeekCount() {
        val workouts = listOf(
            workout("2026-07-05T07:23:05", 7.88),
        )
        val assessment = HealthAssessmentEngine.generate(
            workouts,
            metrics = emptyList(),
            now = LocalDateTime.parse("2026-07-07T22:00:00"),
        )
        val activity = assessment.sections.first { it.title == "Activity" }
        assertTrue(activity.lines.any { it.contains("Progression A (4w): 1") })
        assertTrue(activity.lines.any { it.contains("Latest pace") })
        val profile = assessment.sections.first { it.title == "Profile" }
        assertTrue(profile.lines.any { it.contains("Data through: 2026-07-05") })
    }

    @Test
    fun fourWeekTrend_detectsImprovement() {
        val trend = HealthAssessmentEngine.computeTrend(
            latestWindow = listOf(5.8, 5.9),
            previousWindow = listOf(6.4, 6.5),
            lowerIsBetter = true,
        )
        assertEquals(TrendDirection.IMPROVING, trend)
    }

    @Test
    fun consecutiveWeekStreak_countsBackwardFromLatestActiveWeek() {
        val now = LocalDateTime.parse("2026-07-07T08:00:00")
        val workouts = listOf(
            workout("2026-06-16T08:00:00", 6.0),
            workout("2026-06-23T08:00:00", 6.1),
            workout("2026-06-30T08:00:00", 6.2),
            workout("2026-07-05T07:23:05", 7.88),
        )
        assertEquals(3, ChartDataBuilder.consecutiveWeekStreak(workouts, now))
    }

    @Test
    fun consecutiveWeekStreak_breaksOnMissingWeek() {
        val now = LocalDateTime.parse("2026-07-07T08:00:00")
        val workouts = listOf(
            workout("2026-06-16T08:00:00", 6.0),
            workout("2026-07-05T07:23:05", 7.88),
        )
        assertEquals(1, ChartDataBuilder.consecutiveWeekStreak(workouts, now))
    }

    @Test
    fun weeklyPaceRank_comparesCurrentWeekAgainstAllWeeklyAverages() {
        val now = LocalDateTime.parse("2026-07-07T08:00:00")
        val workouts = listOf(
            workout("2026-06-16T08:00:00", 8.0),
            workout("2026-06-23T08:00:00", 7.0),
            workout("2026-06-30T08:00:00", 6.0),
            workout("2026-07-06T07:30:00", 7.0),
            workout("2026-07-07T07:30:00", 8.0),
        )
        val rank = ChartDataBuilder.weeklyPaceRank(workouts, now)
        assertNotNull(rank)
        assertEquals(7.5, rank!!.pace, 0.01)
        assertEquals(3, rank.rank)
        assertEquals(4, rank.totalWeeks)
    }

    @Test
    fun weekLabelStride_increasesWhenZoomedOut() {
        assertEquals(1, ChartDataBuilder.weekLabelStride(weekWidthPx = 80f, minLabelSpacingPx = 52f))
        assertEquals(2, ChartDataBuilder.weekLabelStride(weekWidthPx = 40f, minLabelSpacingPx = 52f))
        assertEquals(4, ChartDataBuilder.weekLabelStride(weekWidthPx = 14f, minLabelSpacingPx = 52f))
    }

    @Test
    fun buildAxisMarkers_emitsMonthAndYearBoundaries() {
        val weeks = listOf(
            weekBucket(2024, 1, 1, "Jan 1"),
            weekBucket(2024, 1, 2, "Jan 8"),
            weekBucket(2024, 2, 5, "Feb 5"),
            weekBucket(2025, 1, 1, "Jan 1"),
        )
        val markers = ChartDataBuilder.buildAxisMarkers(weeks)
        assertTrue(markers.any { it.type == com.lqborges.garminpacecharts.domain.model.AxisMarkerType.YEAR && it.label == "2024" })
        assertTrue(markers.any { it.type == com.lqborges.garminpacecharts.domain.model.AxisMarkerType.YEAR && it.label == "2025" })
        assertTrue(markers.count { it.type == com.lqborges.garminpacecharts.domain.model.AxisMarkerType.MONTH } >= 3)
    }

    @Test
    fun healthAssessment_showsLastNightAndSevenDayAverage() {
        val now = LocalDateTime.parse("2026-07-07T08:00:00")
        val metrics = listOf(
            metric("SLEEP_SCORE", "2026-07-07T00:00:00", 82.0),
            metric("SLEEP_DURATION", "2026-07-07T00:00:00", 7.4),
            metric("SLEEP_SCORE", "2026-07-06T00:00:00", 70.0),
            metric("SLEEP_DURATION", "2026-07-06T00:00:00", 6.8),
            metric("SLEEP_SCORE", "2026-07-05T00:00:00", 74.0),
            metric("STEPS", "2026-07-06T00:00:00", 9100.0),
            metric("STEPS", "2026-07-05T00:00:00", 8400.0),
            metric("STRESS", "2026-07-06T00:00:00", 28.0),
            metric("STRESS", "2026-07-05T00:00:00", 31.0),
            metric("TRAINING_READINESS", "2026-07-07T00:00:00", 68.0),
            metric("TRAINING_READINESS", "2026-07-06T00:00:00", 55.0),
        )
        val assessment = HealthAssessmentEngine.generate(emptyList(), metrics, now)
        val sleep = assessment.sections.first { it.title == "Sleep" }
        assertTrue(sleep.lines.any { it == "Sleep score (last night): 82" })
        assertTrue(sleep.lines.any { it.startsWith("Sleep score (7d avg):") })
        assertTrue(sleep.lines.any { it == "Sleep duration (last night): 7.4 h" })
        val activity = assessment.sections.first { it.title == "Activity" }
        assertTrue(activity.lines.any { it == "Steps (last day): 9100" })
        assertTrue(activity.lines.any { it.startsWith("Steps (7d avg):") })
        val stress = assessment.sections.first { it.title == "Stress / Recovery" }
        assertTrue(stress.lines.any { it == "Stress (last day): 28" })
        val readiness = assessment.sections.first { it.title == "Training Readiness" }
        assertTrue(readiness.lines.any { it == "Readiness: 68 (prior day 55)" })
    }

    private fun weekBucket(year: Int, month: Int, week: Int, label: String) =
        com.lqborges.garminpacecharts.domain.model.WeekBucket(
            year = year,
            week = week,
            label = label,
            month = month,
            averagePace = 6.0,
            workouts = emptyList(),
        )

    private fun metric(type: String, date: String, value: Double) =
        com.lqborges.garminpacecharts.domain.model.HealthMetricPoint(
            metricType = type,
            date = LocalDateTime.parse(date),
            value = value,
            unit = "",
        )

    private fun workout(date: String, pace: Double) =
        com.lqborges.garminpacecharts.domain.model.Workout(
            activityId = pace.toLong(),
            activityName = "Progression A",
            startTimeLocal = LocalDateTime.parse(date),
            paceMinPerKm = pace,
            paceSource = PaceSource.IMPORTED,
        )
}
