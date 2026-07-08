package com.lqborges.garminpacecharts.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lqborges.garminpacecharts.BuildConfig
import com.lqborges.garminpacecharts.domain.PaceFormatter
import com.lqborges.garminpacecharts.domain.model.Workout

@Composable
fun WorkoutDetailScreen(
    workout: Workout,
    onDelete: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Workout detail", style = MaterialTheme.typography.headlineMedium)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(workout.activityName, style = MaterialTheme.typography.titleMedium)
                Text("Date: ${workout.startTimeLocal}")
                Text("Pace: ${PaceFormatter.toDisplay(workout.paceMinPerKm)} min/km")
                Text("Activity ID: ${workout.activityId ?: "—"}")
                Text("Pace source: ${workout.paceSource}")
            }
        }
        Button(onClick = onDelete, modifier = Modifier.fillMaxWidth()) { Text("Delete local workout") }
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
    }
}

@Composable
fun RefreshScreen(
    summary: com.lqborges.garminpacecharts.domain.model.RefreshSummary?,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onDone: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Refresh Garmin data", style = MaterialTheme.typography.headlineMedium)
        if (isRefreshing) {
            Text("Fetching activities and processing Progression A workouts…")
        }
        Button(onClick = onRefresh, enabled = !isRefreshing, modifier = Modifier.fillMaxWidth()) {
            Text("Fetch latest data")
        }
        summary?.let {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(if (it.status.name == "FAILED") "Refresh failed" else "Refresh complete")
                    it.fetchStartDate?.let { start -> Text("Fetched: $start → ${it.fetchEndDate}") }
                    Text("Activities fetched: ${it.activitiesFetched}")
                    Text("Progression A found: ${it.progressionAFound}")
                    Text("New workouts added: ${it.newWorkoutsAdded}")
                    Text("Skipped duplicates: ${it.duplicatesSkipped}")
                    Text("Total workouts: ${it.totalStored}")
                    Text("Latest workout: ${it.latestWorkoutDate ?: "—"}")
                    it.errorMessage?.let { msg -> Text("Error: $msg") }
                    if (it.skippedActivities.isNotEmpty()) {
                        Text("Skipped:")
                        it.skippedActivities.take(8).forEach { line -> Text("• $line", style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Done") }
    }
}

@Composable
fun SettingsScreen(
    garminConnected: Boolean,
    isRefreshing: Boolean,
    onSyncGarmin: () -> Unit,
    onImportJson: () -> Unit,
    onImportTokens: () -> Unit,
    onExportJson: () -> Unit,
    onClearData: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)
        Text("Version ${BuildConfig.VERSION_NAME} (${BuildConfig.BUILD_LABEL})")
        Text("Garmin: ${if (garminConnected) "connected" else "not connected"}")
        Button(
            onClick = onSyncGarmin,
            enabled = garminConnected && !isRefreshing,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (isRefreshing) "Syncing…" else "Sync Garmin now")
        }
        Button(onClick = onImportJson, modifier = Modifier.fillMaxWidth()) { Text("Import workouts JSON") }
        Button(onClick = onImportTokens, modifier = Modifier.fillMaxWidth()) { Text("Import Garmin tokens") }
        Button(onClick = onExportJson, modifier = Modifier.fillMaxWidth()) { Text("Export workouts JSON") }
        Button(onClick = onClearData, modifier = Modifier.fillMaxWidth()) { Text("Clear local data") }
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
    }
}
