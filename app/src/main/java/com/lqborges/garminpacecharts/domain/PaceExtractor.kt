package com.lqborges.garminpacecharts.domain

import com.lqborges.garminpacecharts.PaceSource
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

data class PaceExtractionResult(
    val paceMinPerKm: Double,
    val paceSource: PaceSource,
    val splitDistanceMeters: Double,
    val splitDurationSeconds: Long,
)

object PaceExtractor {
    const val MIN_PACE = 3.0
    const val MAX_PACE = 9.5
    const val DEFAULT_ACTIVITY_FILTER = "progression a"

    fun matchesActivityName(name: String?, filter: String = DEFAULT_ACTIVITY_FILTER): Boolean {
        if (name.isNullOrBlank()) return false
        return name.lowercase().contains(filter.lowercase())
    }

    fun isValidPace(pace: Double, minPace: Double = MIN_PACE, maxPace: Double = MAX_PACE): Boolean =
        pace > minPace && pace < maxPace

    fun extractFromActivity(activity: JsonObject): PaceExtractionResult? {
        val splits = activity["splitSummaries"]?.jsonArray ?: return null
        return extractFromSplits(splits)
    }

    fun extractFromSplits(splits: JsonArray): PaceExtractionResult? {
        val splitObjects = splits.mapNotNull { it as? JsonObject }
        if (splitObjects.isEmpty()) return null

        val preferred = splitObjects.filter {
            val type = it.splitType()
            type == "INTERVAL_ACTIVE" || type == "RWD_RUN"
        }

        val candidates = if (preferred.isNotEmpty()) {
            preferred
        } else {
            val fallback = splitObjects.filter {
                val type = it.splitType()
                val excluded = type.contains("COOLDOWN", ignoreCase = true) ||
                    type.contains("WARMUP", ignoreCase = true)
                if (excluded) return@filter false
                type.contains("RUN", ignoreCase = true) || type.contains("ACTIVE", ignoreCase = true)
            }
            if (fallback.isEmpty()) emptyList() else listOf(fallback.maxBy { it.durationSeconds() })
        }

        if (candidates.isEmpty()) return null

        val main = candidates.maxBy { it.durationSeconds() }
        val distanceMeters = main.distanceMeters()
        val durationSeconds = main.durationSeconds()
        if (distanceMeters <= 0 || durationSeconds <= 0) return null

        val distanceKm = distanceMeters / 1000.0
        val durationMin = durationSeconds / 60.0
        val pace = durationMin / distanceKm
        if (!isValidPace(pace)) return null

        val source = when (main.splitType()) {
            "INTERVAL_ACTIVE" -> PaceSource.INTERVAL_ACTIVE
            "RWD_RUN" -> PaceSource.RWD_RUN
            else -> PaceSource.FALLBACK_RUN_SPLIT
        }

        return PaceExtractionResult(
            paceMinPerKm = (pace * 100).toLong() / 100.0,
            paceSource = source,
            splitDistanceMeters = distanceMeters,
            splitDurationSeconds = durationSeconds,
        )
    }

    private fun JsonObject.splitType(): String =
        this["splitType"]?.jsonPrimitive?.contentOrNull.orEmpty()

    private fun JsonObject.distanceMeters(): Double =
        GarminJsonNumbers.asDouble(this["distance"])
            ?: GarminJsonNumbers.asDouble(this["splitDistance"])
            ?: 0.0

    private fun JsonObject.durationSeconds(): Long =
        GarminJsonNumbers.asLong(this["duration"])
            ?: GarminJsonNumbers.asLong(this["splitDuration"])
            ?: 0L
}
