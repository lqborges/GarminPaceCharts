package com.lqborges.garminpacecharts.data.local

import com.lqborges.garminpacecharts.PaceSource
import com.lqborges.garminpacecharts.data.local.entity.HealthAssessmentEntity
import com.lqborges.garminpacecharts.data.local.entity.HealthMetricSnapshotEntity
import com.lqborges.garminpacecharts.data.local.entity.RefreshRunEntity
import com.lqborges.garminpacecharts.data.local.entity.WorkoutEntity
import com.lqborges.garminpacecharts.data.local.entity.paceSourceEnum
import com.lqborges.garminpacecharts.domain.model.HealthAssessment
import com.lqborges.garminpacecharts.domain.model.HealthMetricPoint
import com.lqborges.garminpacecharts.domain.model.HealthSection
import com.lqborges.garminpacecharts.domain.model.Workout
import com.lqborges.garminpacecharts.domain.model.epochMillisToLocalDateTime
import com.lqborges.garminpacecharts.domain.model.toEpochMillis
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.time.Instant

private val json = Json { ignoreUnknownKeys = true }

fun WorkoutEntity.toDomain(): Workout = Workout(
    id = id,
    activityId = activityId,
    activityName = activityName,
    startTimeLocal = epochMillisToLocalDateTime(startTimeMillis),
    paceMinPerKm = paceMinPerKm,
    paceSource = paceSourceEnum(),
    distanceMeters = distanceMeters,
    durationSeconds = durationSeconds,
    splitDistanceMeters = splitDistanceMeters,
    splitDurationSeconds = splitDurationSeconds,
    createdAt = Instant.ofEpochMilli(createdAtMillis),
    updatedAt = Instant.ofEpochMilli(updatedAtMillis),
    rawGarminJson = rawGarminJson,
)

fun Workout.toEntity(): WorkoutEntity = WorkoutEntity(
    id = id,
    activityId = activityId,
    activityName = activityName,
    startTimeMillis = startTimeLocal.toEpochMillis(),
    paceMinPerKm = paceMinPerKm,
    paceSource = paceSource.name,
    distanceMeters = distanceMeters,
    durationSeconds = durationSeconds,
    splitDistanceMeters = splitDistanceMeters,
    splitDurationSeconds = splitDurationSeconds,
    createdAtMillis = createdAt.toEpochMilli(),
    updatedAtMillis = updatedAt.toEpochMilli(),
    rawGarminJson = rawGarminJson,
)

fun HealthMetricSnapshotEntity.toDomain(): HealthMetricPoint = HealthMetricPoint(
    metricType = metricType,
    date = epochMillisToLocalDateTime(dateMillis),
    value = value,
    unit = unit,
)

fun HealthAssessmentEntity.toDomain(): HealthAssessment = HealthAssessment(
    id = id,
    generatedAt = Instant.ofEpochMilli(generatedAtMillis),
    dataStartDate = dataStartMillis?.let { epochMillisToLocalDateTime(it) },
    dataEndDate = dataEndMillis?.let { epochMillisToLocalDateTime(it) },
    overallStatus = overallStatus,
    summary = summary,
    strengths = json.decodeFromString(ListSerializer(String.serializer()), strengthsJson),
    concerns = json.decodeFromString(ListSerializer(String.serializer()), concernsJson),
    recommendations = json.decodeFromString(ListSerializer(String.serializer()), recommendationsJson),
    confidence = confidence,
    sourceVersion = sourceVersion,
    sections = json.decodeFromString(ListSerializer(HealthSection.serializer()), sectionsJson),
    dataQualityNotes = json.decodeFromString(ListSerializer(String.serializer()), dataQualityNotesJson),
)

fun HealthAssessment.toEntity(): HealthAssessmentEntity = HealthAssessmentEntity(
    id = id,
    generatedAtMillis = generatedAt.toEpochMilli(),
    dataStartMillis = dataStartDate?.toEpochMillis(),
    dataEndMillis = dataEndDate?.toEpochMillis(),
    overallStatus = overallStatus,
    summary = summary,
    strengthsJson = json.encodeToString(ListSerializer(String.serializer()), strengths),
    concernsJson = json.encodeToString(ListSerializer(String.serializer()), concerns),
    recommendationsJson = json.encodeToString(ListSerializer(String.serializer()), recommendations),
    confidence = confidence,
    sourceVersion = sourceVersion,
    sectionsJson = json.encodeToString(ListSerializer(HealthSection.serializer()), sections),
    dataQualityNotesJson = json.encodeToString(ListSerializer(String.serializer()), dataQualityNotes),
)
