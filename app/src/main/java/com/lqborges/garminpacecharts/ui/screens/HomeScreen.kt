package com.lqborges.garminpacecharts.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lqborges.garminpacecharts.domain.model.DashboardStats
import com.lqborges.garminpacecharts.domain.model.HealthAssessment
import com.lqborges.garminpacecharts.domain.model.WeatherSnapshot
import com.lqborges.garminpacecharts.ui.components.HealthAssessmentContent
import com.lqborges.garminpacecharts.ui.components.WeatherSnapshotCard

@Composable
fun HomeScreen(
    stats: DashboardStats?,
    health: HealthAssessment?,
    weather: WeatherSnapshot?,
    isRefreshing: Boolean,
    isWeatherLoading: Boolean,
    onCharts: () -> Unit,
    onSettings: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        WeatherSnapshotCard(
            snapshot = weather,
            isLoading = isWeatherLoading,
        )

        Text(
            "Health",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )

        HealthAssessmentContent(
            assessment = health,
            stats = stats,
            isRefreshing = isRefreshing,
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onCharts, modifier = Modifier.weight(1f)) { Text("Charts") }
            OutlinedButton(onClick = onSettings, modifier = Modifier.weight(1f)) { Text("Settings") }
        }
    }
}