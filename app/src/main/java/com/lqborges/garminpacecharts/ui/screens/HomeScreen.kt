package com.lqborges.garminpacecharts.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lqborges.garminpacecharts.domain.PaceFormatter
import com.lqborges.garminpacecharts.domain.model.DashboardStats
import com.lqborges.garminpacecharts.domain.model.HealthAssessment

@Composable
fun HomeScreen(
    stats: DashboardStats?,
    health: HealthAssessment?,
    onRefresh: () -> Unit,
    onCharts: () -> Unit,
    onHealth: () -> Unit,
    onSettings: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Dashboard", style = MaterialTheme.typography.headlineMedium)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Progression A", style = MaterialTheme.typography.titleMedium)
                Text("Total workouts: ${stats?.totalWorkouts ?: 0}")
                Text("Latest workout: ${stats?.latestWorkoutDate ?: "—"}")
                stats?.latestPace?.let {
                    Text("Latest pace: ${PaceFormatter.toDisplay(it)} min/km")
                }
                Text("4-week trend: ${stats?.fourWeekTrend ?: "—"}")
                Text("Garmin: ${if (stats?.garminConnected == true) "connected" else "not connected"}")
                Text("Last refresh: ${stats?.lastRefreshAt ?: "never"}")
            }
        }

        health?.let {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Health snapshot", style = MaterialTheme.typography.titleMedium)
                    Text(it.overallStatus)
                    Text(it.summary, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onRefresh, modifier = Modifier.weight(1f)) { Text("Refresh") }
            OutlinedButton(onClick = onCharts, modifier = Modifier.weight(1f)) { Text("Charts") }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onHealth, modifier = Modifier.weight(1f)) { Text("Health") }
            OutlinedButton(onClick = onSettings, modifier = Modifier.weight(1f)) { Text("Settings") }
        }
    }
}
