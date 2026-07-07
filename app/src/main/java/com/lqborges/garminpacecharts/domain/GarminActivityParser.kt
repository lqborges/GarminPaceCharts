package com.lqborges.garminpacecharts.domain

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoField
import java.util.Locale

object DateParser {
    private val GARMIN_DATE_TIME = Regex("""(\d{4})-(\d{2})-(\d{2})[\sT](\d{2}):(\d{2}):(\d{2})""")
    private val GARMIN_DATE_ONLY = Regex("""^(\d{4})-(\d{2})-(\d{2})$""")

    private val FORMATTER_FALLBACKS: List<DateTimeFormatter> = listOf(
        DateTimeFormatter.ISO_LOCAL_DATE_TIME,
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.US),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS", Locale.US),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US),
        DateTimeFormatterBuilder()
            .appendPattern("yyyy-MM-dd['T'][' ']HH:mm:ss")
            .optionalStart()
            .appendFraction(ChronoField.NANO_OF_SECOND, 1, 9, true)
            .optionalEnd()
            .toFormatter(Locale.US),
    )

    fun parseLocalDateTime(value: String): LocalDateTime? {
        val normalized = value.trim()
            .replace('\u00a0', ' ')
            .replace('\u202f', ' ')
            .substringBefore('Z')
            .substringBefore('+')
            .trim()
        if (normalized.isEmpty()) return null

        GARMIN_DATE_TIME.find(normalized)?.let { match ->
            return runCatching {
                LocalDateTime.of(
                    match.groupValues[1].toInt(),
                    match.groupValues[2].toInt(),
                    match.groupValues[3].toInt(),
                    match.groupValues[4].toInt(),
                    match.groupValues[5].toInt(),
                    match.groupValues[6].toInt(),
                )
            }.getOrNull()
        }

        GARMIN_DATE_ONLY.matchEntire(normalized)?.let { match ->
            return runCatching {
                LocalDateTime.of(
                    match.groupValues[1].toInt(),
                    match.groupValues[2].toInt(),
                    match.groupValues[3].toInt(),
                    0,
                    0,
                )
            }.getOrNull()
        }

        val candidates = linkedSetOf(normalized, normalized.replaceFirst(' ', 'T'))
        if (normalized.length >= 19) candidates.add(normalized.take(19))

        for (candidate in candidates) {
            for (formatter in FORMATTER_FALLBACKS) {
                try {
                    return LocalDateTime.parse(candidate, formatter)
                } catch (_: DateTimeParseException) {
                    // try next
                }
            }
        }
        return null
    }

    fun parseEpochMillis(value: Number): LocalDateTime? {
        var millis = value.toLong()
        if (millis in 1..99_999_999_999L) millis *= 1000
        if (millis <= 0L) return null
        return Instant.ofEpochMilli(millis)
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime()
    }
}

object GarminJsonNumbers {
    fun asLong(element: JsonElement?): Long? {
        val primitive = element?.jsonPrimitive ?: return null
        primitive.longOrNull?.let { return it }
        primitive.doubleOrNull?.let { return it.toLong() }
        return primitive.contentOrNull?.toDoubleOrNull()?.toLong()
    }

    fun asDouble(element: JsonElement?): Double? {
        val primitive = element?.jsonPrimitive ?: return null
        primitive.doubleOrNull?.let { return it }
        primitive.longOrNull?.let { return it.toDouble() }
        return primitive.contentOrNull?.toDoubleOrNull()
    }
}

object GarminActivityParser {
    private val START_FIELDS = listOf(
        "startTimeLocal",
        "startTimeGMT",
        "beginTimestamp",
        "startTimestamp",
    )

    fun parseStartTime(activity: JsonObject): LocalDateTime? {
        for (field in START_FIELDS) {
            parseFromField(activity, field)?.let { return it }
        }
        return activity.entries.firstNotNullOfOrNull { (key, value) ->
            if (!key.contains("start", ignoreCase = true) &&
                !key.contains("begin", ignoreCase = true)
            ) {
                return@firstNotNullOfOrNull null
            }
            parseFromElement(value)
        }
    }

    fun parseActivityId(activity: JsonObject): Long? =
        GarminJsonNumbers.asLong(activity["activityId"])

    fun formatStartFields(activity: JsonObject): String {
        val parts = START_FIELDS.mapNotNull { field ->
            when (val element = activity[field]) {
                null -> null
                JsonNull -> "$field=null"
                is JsonPrimitive -> "$field=${element.contentOrNull ?: element}"
                else -> "$field=${element}"
            }
        }
        return parts.joinToString(", ").ifEmpty { "no date fields" }
    }

    private fun parseFromField(activity: JsonObject, field: String): LocalDateTime? =
        parseFromElement(activity[field])

    private fun parseFromElement(element: JsonElement?): LocalDateTime? {
        when (element) {
            null, JsonNull -> return null
            is JsonPrimitive -> {
                val text = element.contentOrNull?.trim().orEmpty()
                if (text.isNotEmpty()) {
                    if (looksLikeDateTime(text)) {
                        DateParser.parseLocalDateTime(text)?.let { return it }
                    }
                    text.toLongOrNull()?.let { epoch ->
                        DateParser.parseEpochMillis(epoch)?.let { return it }
                    }
                }
                GarminJsonNumbers.asLong(element)?.let { millis ->
                    DateParser.parseEpochMillis(millis)?.let { return it }
                }
            }
            else -> {
                GarminJsonNumbers.asLong(element)?.let { millis ->
                    DateParser.parseEpochMillis(millis)?.let { return it }
                }
            }
        }
        return null
    }

    private fun looksLikeDateTime(value: String): Boolean =
        value.contains('-') && (value.contains(':') || value.contains('T') || value.contains(' '))
}
