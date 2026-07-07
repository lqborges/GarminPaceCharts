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
    fun fourWeekTrend_detectsImprovement() {
        val trend = HealthAssessmentEngine.computeTrend(
            latestWindow = listOf(5.8, 5.9),
            previousWindow = listOf(6.4, 6.5),
            lowerIsBetter = true,
        )
        assertEquals(TrendDirection.IMPROVING, trend)
    }

    private fun workout(date: String, pace: Double) =
        com.lqborges.garminpacecharts.domain.model.Workout(
            activityId = pace.toLong(),
            activityName = "Progression A",
            startTimeLocal = LocalDateTime.parse(date),
            paceMinPerKm = pace,
            paceSource = PaceSource.IMPORTED,
        )
}
