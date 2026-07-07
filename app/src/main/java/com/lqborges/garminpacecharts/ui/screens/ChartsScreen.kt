package com.lqborges.garminpacecharts.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lqborges.garminpacecharts.ChartRange
import com.lqborges.garminpacecharts.domain.PaceFormatter
import com.lqborges.garminpacecharts.domain.model.ChartData
import com.lqborges.garminpacecharts.domain.model.Workout
import com.lqborges.garminpacecharts.ui.components.PaceChart

@Composable
fun ChartsScreen(
    chartData: ChartData?,
    chartRange: ChartRange,
    workouts: List<Workout>,
    onRangeSelected: (ChartRange) -> Unit,
    onWorkoutSelected: (Workout) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Charts", style = MaterialTheme.typography.headlineMedium)
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ChartRange.entries.forEach { range ->
                FilterChip(
                    selected = chartRange == range,
                    onClick = { onRangeSelected(range) },
                    label = {
                        Text(
                            when (range) {
                                ChartRange.LAST_4_WEEKS -> "4 Weeks"
                                ChartRange.LAST_YEAR -> "1 Year"
                                ChartRange.ALL_TIME -> "All Time"
                            },
                        )
                    },
                )
            }
        }

        chartData?.let {
            Text(it.title, style = MaterialTheme.typography.titleMedium)
            PaceChart(chartData = it, onWorkoutSelected = onWorkoutSelected)
        } ?: Text("Import workouts to view charts.")

        Text("Workouts", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(workouts.sortedByDescending { it.startTimeLocal }) { workout ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onWorkoutSelected(workout) },
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(workout.activityName)
                        Text("${workout.startTimeLocal} • ${PaceFormatter.toDisplay(workout.paceMinPerKm)}")
                    }
                }
            }
        }
    }
}
