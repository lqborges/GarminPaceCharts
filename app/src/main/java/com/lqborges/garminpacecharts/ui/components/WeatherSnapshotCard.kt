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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.lqborges.garminpacecharts.domain.WeatherFormatter
import com.lqborges.garminpacecharts.domain.model.WeatherSnapshot

private val RainLow = Color(0xFF81C784)
private val RainModerate = Color(0xFFFFB74D)
private val RainHigh = Color(0xFF42A5F5)

@Composable
fun WeatherSnapshotCard(
    snapshot: WeatherSnapshot?,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        when {
            snapshot != null -> WeatherSnapshotBody(snapshot)
            isLoading -> Text(
                "Loading weather…",
                modifier = Modifier.padding(14.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
            else -> Text(
                "Weather unavailable",
                modifier = Modifier.padding(14.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WeatherSnapshotBody(snapshot: WeatherSnapshot) {
    Column(
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                snapshot.locationName,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                WeatherFormatter.formatTemperature(snapshot.temperatureC),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }

        TemperatureBar(snapshot.temperatureC)

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "Rain next hour",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "${snapshot.rainChanceNextHourPercent}%",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = rainColor(snapshot.rainChanceNextHourPercent),
                )
            }
            LinearProgressIndicator(
                progress = { snapshot.rainChanceNextHourPercent / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = rainColor(snapshot.rainChanceNextHourPercent),
                trackColor = MaterialTheme.colorScheme.surface,
            )
        }
    }
}

@Composable
private fun TemperatureBar(temperatureC: Double) {
    val normalized = ((temperatureC + 5.0) / 35.0).coerceIn(0.0, 1.0).toFloat()
    val barColor = when {
        temperatureC >= 22 -> Color(0xFFE57373)
        temperatureC >= 12 -> Color(0xFFFFB74D)
        else -> Color(0xFF64B5F6)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surface),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(normalized)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(barColor),
        )
    }
}

private fun rainColor(percent: Int): Color = when {
    percent >= 60 -> RainHigh
    percent >= 30 -> RainModerate
    else -> RainLow
}