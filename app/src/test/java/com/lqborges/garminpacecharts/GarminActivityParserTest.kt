package com.lqborges.garminpacecharts.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class GarminActivityParserTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun parseStartTime_handlesGarminSpaceFormat() {
        val t = GarminActivityParser.parseStartTime(
            json.parseToJsonElement("""{"startTimeLocal":"2026-07-05 07:23:05"}""").jsonObject,
        )
        assertEquals(LocalDateTime.of(2026, 7, 5, 7, 23, 5), t)
    }

    @Test
    fun parseStartTime_fallsBackToBeginTimestamp() {
        val t = GarminActivityParser.parseStartTime(
            json.parseToJsonElement("""{"beginTimestamp":1783232585000}""").jsonObject,
        )
        assertNotNull(t)
        assertEquals(2026, t!!.year)
        assertEquals(7, t.monthValue)
        assertEquals(5, t.dayOfMonth)
    }

    @Test
    fun parseActivity_fixtureFromGarminApi() {
        val raw = this::class.java.getResourceAsStream("/garmin_activity_list_sample.json")!!
            .bufferedReader().readText()
        val activities = json.parseToJsonElement(raw).jsonArray
        assertTrue(activities.size >= 1)
        activities.forEach { item ->
            val activity = item.jsonObject
            val name = activity["activityName"]?.jsonPrimitive?.contentOrNull
            val start = GarminActivityParser.parseStartTime(activity)
            assertNotNull("missing start for $name", start)
        }
        val withPace = activities.count {
            PaceExtractor.extractFromActivity(it.jsonObject) != null
        }
        assertTrue("expected most fixture activities to yield pace", withPace >= activities.size - 1)
    }
}
