package com.lqborges.garminpacecharts.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lqborges.garminpacecharts.domain.ChartDataBuilder
import com.lqborges.garminpacecharts.domain.HealthAssessmentEngine
import com.lqborges.garminpacecharts.domain.model.DashboardStats
import com.lqborges.garminpacecharts.domain.model.HealthAssessment
import com.lqborges.garminpacecharts.domain.model.HealthSection
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val StatusGood = Color(0xFF2E7D32)
private val StatusStable = Color(0xFFF57C00)
private val StatusAttention = Color(0xFFC62828)

@Composable
fun HealthAssessmentContent(
    assessment: HealthAssessment?,
    stats: DashboardStats?,
    isRefreshing: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (isRefreshing) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    "Syncing Garmin data…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        if (assessment == null) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("No assessment yet", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (isRefreshing) "Fetching your latest workouts and wellness data."
                        else "Import workouts or connect Garmin to generate your health assessment.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            return@Column
        }

        StatusHeroCard(assessment = assessment, stats = stats)

        val hiddenSections = setOf("Profile", "Cardio")
        assessment.sections
            .filter { it.title !in hiddenSections }
            .forEach { section ->
                MetricSectionCard(section = section)
            }

        if (assessment.strengths.isNotEmpty() || assessment.concerns.isNotEmpty()) {
            InsightCard(
                strengths = assessment.strengths,
                concerns = assessment.concerns,
            )
        }

        if (assessment.recommendations.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Recommendations", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    assessment.recommendations.forEachIndexed { index, rec ->
                        Text(
                            "${index + 1}. $rec",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }

        val qualityNotes = assessment.dataQualityNotes.filter { it != HealthAssessmentEngine.DISCLAIMER }
        if (qualityNotes.isNotEmpty()) {
            Text(
                qualityNotes.joinToString(" "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            HealthAssessmentEngine.DISCLAIMER,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatusHeroCard(
    assessment: HealthAssessment,
    stats: DashboardStats?,
) {
    val statusColor = when {
        assessment.overallStatus.contains("Good", ignoreCase = true) -> StatusGood
        assessment.overallStatus.contains("attention", ignoreCase = true) -> StatusAttention
        else -> StatusStable
    }
    val vo2Line = assessment.sections.firstOrNull { it.title == "Cardio" }
        ?.lines?.firstOrNull { it.startsWith("VO2") }
    val restingHrLine = assessment.sections.firstOrNull { it.title == "Cardio" }
        ?.lines?.firstOrNull { it.startsWith("Resting HR") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        assessment.overallStatus,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        "Confidence: ${assessment.confidence}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    )
                }
                Surface(
                    color = statusColor,
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(
                        "●",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                HeroMetric(
                    label = "VO₂ max",
                    value = vo2Line?.substringAfter(": ")?.takeIf { it != "no data" } ?: "—",
                    modifier = Modifier.weight(1f),
                )
                HeroMetric(
                    label = "Resting HR",
                    value = restingHrLine?.substringAfter(": ")?.takeIf { it != "no data" } ?: "—",
                    modifier = Modifier.weight(1f),
                )
            }

            stats?.let { runningStats(it) }

            AssessmentMeta(assessment = assessment, stats = stats)
        }
    }
}

@Composable
private fun runningStats(stats: DashboardStats) {
    val lines = buildList {
        if (stats.consecutiveWeekStreak > 0) {
            val weekLabel = if (stats.consecutiveWeekStreak == 1) "week" else "weeks"
            add("Streak: ${stats.consecutiveWeekStreak} $weekLabel")
        }
        stats.weeklyPaceRank?.let { rank ->
            add(ChartDataBuilder.formatWeeklyPaceRank(rank))
        }
        stats.fourWeekAveragePace?.let { pace ->
            add("4w avg pace: ${com.lqborges.garminpacecharts.domain.PaceFormatter.toDisplay(pace)}")
        }
    }
    if (lines.isEmpty()) return

    HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f))
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "Progression A",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        lines.forEach { line ->
            Text(
                line,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f),
            )
        }
    }
}

@Composable
private fun HeroMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun AssessmentMeta(
    assessment: HealthAssessment,
    stats: DashboardStats?,
) {
    val generated = DateTimeFormatter.ofPattern("MMM d, HH:mm")
        .format(assessment.generatedAt.atZone(ZoneId.systemDefault()))
    val meta = buildList {
        add("Updated $generated")
        assessment.dataEndDate?.let { add("Data through ${it.toLocalDate()}") }
        stats?.lastRefreshAt?.let { add("Garmin sync ${formatInstant(it)}") }
    }
    Text(
        meta.joinToString(" · "),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
    )
}

@Composable
private fun MetricSectionCard(section: HealthSection) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                section.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            section.lines.forEach { line ->
                MetricLine(line = line)
            }
        }
    }
}

@Composable
private fun MetricLine(line: String) {
    val parts = line.split(": ", limit = 2)
    if (parts.size == 2) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(parts[0], style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(parts[1], style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        }
    } else {
        Text(line, style = MaterialTheme.typography.bodyMedium)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InsightCard(
    strengths: List<String>,
    concerns: List<String>,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (strengths.isNotEmpty()) {
                Text("Strengths", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    strengths.forEach { Chip(text = it, color = StatusGood.copy(alpha = 0.12f), textColor = StatusGood) }
                }
            }
            if (concerns.isNotEmpty()) {
                Text("Watch", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    concerns.forEach { Chip(text = it, color = StatusAttention.copy(alpha = 0.12f), textColor = StatusAttention) }
                }
            }
        }
    }
}

@Composable
private fun Chip(
    text: String,
    color: Color,
    textColor: Color,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = textColor)
    }
}

private fun formatInstant(instant: Instant): String =
    DateTimeFormatter.ofPattern("MMM d, HH:mm").format(instant.atZone(ZoneId.systemDefault()))