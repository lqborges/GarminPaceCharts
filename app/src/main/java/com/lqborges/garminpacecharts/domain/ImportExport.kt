package com.lqborges.garminpacecharts.domain

import com.lqborges.garminpacecharts.PaceSource
import com.lqborges.garminpacecharts.domain.model.ExportWorkoutRow
import com.lqborges.garminpacecharts.domain.model.ImportResult
import com.lqborges.garminpacecharts.domain.model.ImportRow
import com.lqborges.garminpacecharts.domain.model.Workout
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.time.Instant
import java.time.format.DateTimeFormatter

object ImportExport {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val exportSerializer = ListSerializer(ExportWorkoutRow.serializer())
    private val isoFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    fun parseImportJson(raw: String): Pair<List<ImportRow>, List<String>> {
        val invalid = mutableListOf<String>()
        val rows = mutableListOf<ImportRow>()

        val element = runCatching { json.parseToJsonElement(raw) }.getOrElse {
            return emptyList<ImportRow>() to listOf("Invalid JSON: ${it.message}")
        }

        val array = element as? JsonArray ?: return emptyList<ImportRow>() to listOf("Expected JSON array")

        array.forEachIndexed { index, item ->
            val obj = item as? JsonObject
            if (obj == null) {
                invalid.add("Row $index: not an object")
                return@forEachIndexed
            }

            val date = obj["date"]?.jsonPrimitive?.content
            val pace = obj["pace"]?.jsonPrimitive?.doubleOrNull
            val name = obj["name"]?.jsonPrimitive?.content
            val activityId = obj["activity_id"]?.jsonPrimitive?.longOrNull

            when {
                date.isNullOrBlank() -> invalid.add("Row $index: missing date")
                pace == null -> invalid.add("Row $index: missing or invalid pace")
                name.isNullOrBlank() -> invalid.add("Row $index: missing name")
                !PaceExtractor.isValidPace(pace) -> invalid.add("Row $index: pace out of range ($pace)")
                DateParser.parseLocalDateTime(date) == null -> invalid.add("Row $index: invalid date ($date)")
                else -> rows.add(ImportRow(date = date, pace = pace, activityId = activityId, name = name))
            }
        }

        return rows to invalid
    }

    fun mergeImports(
        existing: List<Workout>,
        rows: List<ImportRow>,
    ): Pair<List<Workout>, ImportResult> {
        val existingByActivityId = existing.filter { it.activityId != null }.associateBy { it.activityId!! }
        val existingFallbackKeys = existing.map { fallbackKey(it) }.toMutableSet()
        val merged = existing.toMutableList()
        var imported = 0
        var duplicates = 0

        rows.forEach { row ->
            val start = DateParser.parseLocalDateTime(row.date) ?: return@forEach
            if (row.activityId != null && existingByActivityId.containsKey(row.activityId)) {
                duplicates++
                return@forEach
            }
            val key = "${row.date}|${row.name}|${row.pace}"
            if (row.activityId == null && existingFallbackKeys.contains(key)) {
                duplicates++
                return@forEach
            }

            val now = Instant.now()
            merged.add(
                Workout(
                    activityId = row.activityId,
                    activityName = row.name,
                    startTimeLocal = start,
                    paceMinPerKm = row.pace,
                    paceSource = PaceSource.IMPORTED,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            if (row.activityId == null) existingFallbackKeys.add(key) else existingByActivityId[row.activityId]
            imported++
        }

        val sorted = merged.sortedBy { it.startTimeLocal }
        return sorted to ImportResult(
            imported = imported,
            duplicatesSkipped = duplicates,
            invalidRows = emptyList(),
            totalStored = sorted.size,
        )
    }

    fun exportToJson(workouts: List<Workout>): String {
        val rows = workouts.sortedBy { it.startTimeLocal }.map { workout ->
            ExportWorkoutRow(
                date = workout.startTimeLocal.format(isoFormatter),
                pace = workout.paceMinPerKm,
                activity_id = workout.activityId,
                name = workout.activityName,
            )
        }
        return json.encodeToString(exportSerializer, rows)
    }

    private fun fallbackKey(workout: Workout): String =
        "${workout.startTimeLocal.format(isoFormatter)}|${workout.activityName}|${workout.paceMinPerKm}"
}
