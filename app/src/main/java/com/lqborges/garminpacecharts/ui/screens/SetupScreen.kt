package com.lqborges.garminpacecharts.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lqborges.garminpacecharts.domain.model.ImportResult

@Composable
fun SetupScreen(
    importResult: ImportResult?,
    onImportJson: () -> Unit,
    onImportTokens: () -> Unit,
    onContinue: () -> Unit,
    hasWorkouts: Boolean,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Welcome", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Import your existing progression_a_workouts.json to view charts offline. " +
                "Garmin token import is optional for later refresh.",
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Data setup", style = MaterialTheme.typography.titleMedium)
                OutlinedButton(
                    onClick = onImportJson,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Import workouts JSON")
                }
                OutlinedButton(
                    onClick = onImportTokens,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Import Garmin tokens (optional)")
                }
            }
        }

        importResult?.let { result ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Import summary", style = MaterialTheme.typography.titleMedium)
                    Text("Imported: ${result.imported}")
                    Text("Duplicates skipped: ${result.duplicatesSkipped}")
                    Text("Total stored: ${result.totalStored}")
                    if (result.invalidRows.isNotEmpty()) {
                        Text("Invalid rows: ${result.invalidRows.size}")
                        result.invalidRows.take(5).forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))
        Button(
            onClick = onContinue,
            enabled = hasWorkouts,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Continue to dashboard")
        }
    }
}
