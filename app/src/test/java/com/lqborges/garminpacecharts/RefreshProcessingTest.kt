package com.lqborges.garminpacecharts.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RefreshProcessingTest {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Test
    fun parse_epochStringInBeginTimestamp() {
        val activity = json.parseToJsonElement(
            """{"beginTimestamp":"1783232585000"}""",
        ).jsonObject
        val start = GarminActivityParser.parseStartTime(activity)
        assertNotNull(start)
        assertEquals(2026, start!!.year)
        assertEquals(7, start.monthValue)
    }

    @Test
    fun parseStartTime_nullStringFieldFallsBackToBeginTimestamp() {
        val activity = json.parseToJsonElement(
            """{"startTimeLocal":null,"beginTimestamp":1783232585000}""",
        ).jsonObject
        val start = GarminActivityParser.parseStartTime(activity)
        assertNotNull(start)
        assertTrue(start!!.year == 2026 && start.monthValue == 7)
    }

    @Test
    fun liveApiFixture_allProgressionAActivitiesParseDates() {
        val raw = this::class.java.getResourceAsStream("/garmin_activities_jun8_jul7.json")!!
            .bufferedReader().readText()
        val activities = json.parseToJsonElement(raw).jsonArray
        val progression = activities.map { it.jsonObject }.filter {
            PaceExtractor.matchesActivityName(it["activityName"]?.jsonPrimitive?.contentOrNull)
        }
        assertTrue(progression.size >= 10)
        progression.forEach { activity ->
            val name = activity["activityName"]?.jsonPrimitive?.contentOrNull
            val start = GarminActivityParser.parseStartTime(activity)
            assertNotNull("missing start for $name", start)
        }
    }

    @Test
    fun liveApiFixture_extractsPaceForValidActivities() {
        val raw = this::class.java.getResourceAsStream("/garmin_activities_jun8_jul7.json")!!
            .bufferedReader().readText()
        val activities = json.parseToJsonElement(raw).jsonArray
        val progression = activities.map { it.jsonObject }.filter {
            PaceExtractor.matchesActivityName(it["activityName"]?.jsonPrimitive?.contentOrNull)
        }
        val withPace = progression.count { PaceExtractor.extractFromActivity(it) != null }
        // One June 26 activity has pace ~10.6 min/km (out of range)
        assertTrue("expected most activities to yield pace", withPace >= progression.size - 1)
    }
}
