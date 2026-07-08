package com.lqborges.garminpacecharts.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lqborges.garminpacecharts.domain.MetricChange
import com.lqborges.garminpacecharts.domain.MetricSentiment
import com.lqborges.garminpacecharts.domain.model.HealthMetricDisplay

private val PositiveChange = Color(0xFF2E7D32)
private val NegativeChange = Color(0xFFC62828)
private val NeutralChange = Color(0xFF757575)

@Composable
fun MetricRow(metric: HealthMetricDisplay, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    metric.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                metric.priorValue?.let { prior ->
                    Text(
                        "Prior: $prior",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    metric.value,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                PercentChangeBadge(
                    percentChange = metric.percentChange,
                    lowerIsBetter = metric.lowerIsBetter,
                )
            }
        }
        metric.progress?.let { progress ->
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }
    }
}

@Composable
fun PercentChangeBadge(
    percentChange: Double?,
    lowerIsBetter: Boolean,
    modifier: Modifier = Modifier,
) {
    val formatted = MetricChange.formatPercent(percentChange)
    if (formatted == null) return

    val sentiment = MetricChange.sentiment(percentChange, lowerIsBetter)
    val (background, foreground) = when (sentiment) {
        MetricSentiment.POSITIVE -> PositiveChange.copy(alpha = 0.15f) to PositiveChange
        MetricSentiment.NEGATIVE -> NegativeChange.copy(alpha = 0.15f) to NegativeChange
        MetricSentiment.NEUTRAL -> NeutralChange.copy(alpha = 0.12f) to NeutralChange
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            "${MetricChange.arrow(sentiment)} $formatted",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = foreground,
        )
    }
}