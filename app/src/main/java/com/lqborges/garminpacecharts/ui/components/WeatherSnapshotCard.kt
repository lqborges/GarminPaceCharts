package com.lqborges.garminpacecharts.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lqborges.garminpacecharts.domain.WeatherFormatter
import com.lqborges.garminpacecharts.domain.model.WeatherSnapshot

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
        verticalArrangement = Arrangement.spacedBy(6.dp),
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
        Text(
            WeatherFormatter.describeWeather(snapshot.weatherCode),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            buildString {
                append("Feels ${WeatherFormatter.formatTemperature(snapshot.apparentTemperatureC)}")
                append(" · Wind ${WeatherFormatter.formatWind(snapshot.windSpeedKmh)}")
                append(" · Humidity ${snapshot.humidityPercent}%")
                if (snapshot.precipitationMm > 0.0) {
                    append(" · Rain ${"%.1f".format(snapshot.precipitationMm)} mm")
                }
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}