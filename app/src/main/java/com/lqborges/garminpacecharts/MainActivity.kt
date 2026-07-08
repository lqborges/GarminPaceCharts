package com.lqborges.garminpacecharts

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lqborges.garminpacecharts.ui.AppViewModel
import com.lqborges.garminpacecharts.ui.AppViewModelFactory
import com.lqborges.garminpacecharts.ui.navigation.Routes
import com.lqborges.garminpacecharts.ui.screens.ChartsScreen
import com.lqborges.garminpacecharts.ui.screens.HomeScreen
import com.lqborges.garminpacecharts.ui.screens.RefreshScreen
import com.lqborges.garminpacecharts.ui.screens.SettingsScreen
import com.lqborges.garminpacecharts.ui.screens.SetupScreen
import com.lqborges.garminpacecharts.ui.screens.WorkoutDetailScreen
import com.lqborges.garminpacecharts.ui.theme.GarminPaceChartsTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as GarminPaceChartsApp).container

        setContent {
            GarminPaceChartsTheme {
                val viewModel: AppViewModel = viewModel(factory = AppViewModelFactory(container))
                val navController = rememberNavController()
                val snackbarHostState = remember { SnackbarHostState() }
                val scope = rememberCoroutineScope()

                val setupComplete by viewModel.setupComplete.collectAsState()
                val workouts by viewModel.workouts.collectAsState()
                val dashboardStats by viewModel.dashboardStats.collectAsState()
                val healthAssessment by viewModel.healthAssessment.collectAsState()
                val chartData by viewModel.chartData.collectAsState()
                val chartRange by viewModel.chartRange.collectAsState()
                val importResult by viewModel.importResult.collectAsState()
                val refreshSummary by viewModel.refreshSummary.collectAsState()
                val message by viewModel.message.collectAsState()
                val isRefreshing by viewModel.isRefreshing.collectAsState()
                val weatherSnapshot by viewModel.weatherSnapshot.collectAsState()
                val isWeatherLoading by viewModel.isWeatherLoading.collectAsState()

                val importJsonLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenDocument(),
                ) { uri ->
                    if (uri != null) {
                        scope.launch {
                            val text = withContext(Dispatchers.IO) {
                                contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                            }
                            if (!text.isNullOrBlank()) viewModel.importJson(text)
                        }
                    }
                }

                val importTokenLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenDocument(),
                ) { uri ->
                    if (uri != null) {
                        scope.launch {
                            val text = withContext(Dispatchers.IO) {
                                contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                            }
                            if (!text.isNullOrBlank()) viewModel.importGarminTokens(text)
                        }
                    }
                }

                val exportJsonLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.CreateDocument("application/json"),
                ) { uri ->
                    if (uri != null) {
                        scope.launch {
                            val json = viewModel.exportJsonAsync()
                            withContext(Dispatchers.IO) {
                                contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(json) }
                            }
                            snackbarHostState.showSnackbar("Exported workouts JSON")
                        }
                    }
                }

                LaunchedEffect(message) {
                    message?.let {
                        snackbarHostState.showSnackbar(it)
                        viewModel.clearMessage()
                    }
                }

                LaunchedEffect(setupComplete, workouts.size) {
                    if (setupComplete && workouts.isNotEmpty()) {
                        val current = navController.currentDestination?.route
                        if (current == Routes.SETUP) {
                            navController.navigate(Routes.HOME) {
                                popUpTo(Routes.SETUP) { inclusive = true }
                            }
                        }
                    }
                }

                Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
                    NavHost(
                        navController = navController,
                        startDestination = if (setupComplete && workouts.isNotEmpty()) Routes.HOME else Routes.SETUP,
                        modifier = Modifier.padding(padding),
                    ) {
                        composable(Routes.SETUP) {
                            SetupScreen(
                                importResult = importResult,
                                onImportJson = { importJsonLauncher.launch(arrayOf("application/json", "text/*", "*/*")) },
                                onImportTokens = { importTokenLauncher.launch(arrayOf("application/json", "text/*", "*/*")) },
                                onContinue = {
                                    viewModel.completeSetup()
                                    navController.navigate(Routes.HOME) {
                                        popUpTo(Routes.SETUP) { inclusive = true }
                                    }
                                },
                                hasWorkouts = workouts.isNotEmpty(),
                            )
                        }
                        composable(Routes.HOME) {
                            HomeScreen(
                                stats = dashboardStats,
                                health = healthAssessment,
                                weather = weatherSnapshot,
                                isRefreshing = isRefreshing,
                                isWeatherLoading = isWeatherLoading,
                                onCharts = { navController.navigate(Routes.CHARTS) },
                                onSettings = { navController.navigate(Routes.SETTINGS) },
                            )
                        }
                        composable(Routes.CHARTS) {
                            ChartsScreen(
                                chartData = chartData,
                                chartRange = chartRange,
                                workouts = workouts,
                                stats = dashboardStats,
                                onRangeSelected = viewModel::setChartRange,
                                onWorkoutSelected = { workout ->
                                    navController.navigate(Routes.workoutDetail(workout.id))
                                },
                            )
                        }
                        composable(
                            route = Routes.WORKOUT_DETAIL,
                            arguments = listOf(navArgument("workoutId") { type = NavType.LongType }),
                        ) { entry ->
                            val workoutId = entry.arguments?.getLong("workoutId") ?: return@composable
                            var workout by remember(workoutId) { mutableStateOf<com.lqborges.garminpacecharts.domain.model.Workout?>(null) }
                            LaunchedEffect(workoutId) {
                                workout = viewModel.getWorkout(workoutId)
                            }
                            workout?.let {
                                WorkoutDetailScreen(
                                    workout = it,
                                    onDelete = {
                                        viewModel.deleteWorkout(workoutId)
                                        navController.popBackStack()
                                    },
                                    onBack = { navController.popBackStack() },
                                )
                            }
                        }
                        composable(Routes.REFRESH) {
                            RefreshScreen(
                                summary = refreshSummary,
                                isRefreshing = isRefreshing,
                                onRefresh = {
                                    scope.launch { viewModel.refresh() }
                                },
                                onDone = { navController.popBackStack() },
                            )
                        }
                        composable(Routes.SETTINGS) {
                            SettingsScreen(
                                garminConnected = container.isGarminConnected(),
                                isRefreshing = isRefreshing,
                                onSyncGarmin = { scope.launch { viewModel.refresh() } },
                                onImportJson = { importJsonLauncher.launch(arrayOf("application/json", "text/*", "*/*")) },
                                onImportTokens = { importTokenLauncher.launch(arrayOf("application/json", "text/*", "*/*")) },
                                onExportJson = { exportJsonLauncher.launch("progression_a_workouts.json") },
                                onClearData = { viewModel.clearAllData() },
                                onBack = { navController.popBackStack() },
                            )
                        }
                    }
                }
            }
        }
    }
}
