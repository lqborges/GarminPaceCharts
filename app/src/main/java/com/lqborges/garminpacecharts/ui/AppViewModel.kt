package com.lqborges.garminpacecharts.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lqborges.garminpacecharts.AppContainer
import com.lqborges.garminpacecharts.ChartRange
import com.lqborges.garminpacecharts.domain.ChartDataBuilder
import com.lqborges.garminpacecharts.domain.model.ChartData
import com.lqborges.garminpacecharts.domain.model.DashboardStats
import com.lqborges.garminpacecharts.domain.model.HealthAssessment
import com.lqborges.garminpacecharts.domain.model.ImportResult
import com.lqborges.garminpacecharts.domain.model.RefreshSummary
import com.lqborges.garminpacecharts.domain.model.Workout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant

class AppViewModel(private val container: AppContainer) : ViewModel() {
    init {
        viewModelScope.launch {
            combine(
                container.workoutRepository.observeWorkouts(),
                container.preferencesManager.lastRefreshAt,
            ) { workoutList, lastRefreshMs ->
                workoutList to lastRefreshMs?.let { Instant.ofEpochMilli(it) }
            }.collect { (workoutList, lastRefreshAt) ->
                container.healthRepository.regenerateIfStale(workoutList, lastRefreshAt)
            }
        }
    }

    val setupComplete = container.preferencesManager.setupComplete
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val workouts = container.workoutRepository.observeWorkouts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val dashboardStats = container.workoutRepository
        .observeDashboardStats(container.isGarminConnected())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val healthAssessment = container.healthRepository.observeLatestAssessment()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _chartRange = MutableStateFlow(ChartRange.LAST_4_WEEKS)
    val chartRange = _chartRange.asStateFlow()

    val chartData: StateFlow<ChartData?> = combine(workouts, chartRange) { list, range ->
        if (list.isEmpty()) null else ChartDataBuilder.build(list, range)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _importResult = MutableStateFlow<ImportResult?>(null)
    val importResult = _importResult.asStateFlow()

    private val _refreshSummary = MutableStateFlow<RefreshSummary?>(null)
    val refreshSummary = _refreshSummary.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    fun setChartRange(range: ChartRange) {
        _chartRange.value = range
    }

    fun importJson(raw: String) {
        viewModelScope.launch {
            val result = container.workoutRepository.importJson(raw)
            _importResult.value = result
            if (result.totalStored > 0) {
                container.healthRepository.regenerateAssessment()
            }
            if (result.imported > 0 || result.totalStored > 0) {
                container.preferencesManager.setSetupComplete(true)
            }
        }
    }

    fun exportJson(): String = kotlinx.coroutines.runBlocking {
        container.workoutRepository.exportJson()
    }

    suspend fun exportJsonAsync(): String = container.workoutRepository.exportJson()

    suspend fun refresh(): RefreshSummary {
        val summary = container.refreshRepository.refresh()
        _refreshSummary.value = summary
        return summary
    }

    fun completeSetup() {
        viewModelScope.launch {
            container.preferencesManager.setSetupComplete(true)
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    fun importGarminTokens(raw: String) {
        viewModelScope.launch {
            runCatching {
                container.garminTokenStore.saveFromJson(raw)
                _message.value = "Garmin tokens imported"
            }.onFailure {
                _message.value = it.message ?: "Token import failed"
            }
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            container.workoutRepository.clearAll()
            container.garminTokenStore.clear()
            container.preferencesManager.setSetupComplete(false)
            _message.value = "Local data cleared"
        }
    }

    suspend fun getWorkout(id: Long): Workout? = container.workoutRepository.getWorkout(id)

    fun deleteWorkout(id: Long) {
        viewModelScope.launch {
            container.workoutRepository.deleteWorkout(id)
            container.healthRepository.regenerateAssessment()
        }
    }

    fun regenerateHealthAssessment() {
        viewModelScope.launch {
            container.healthRepository.regenerateAssessment()
        }
    }
}

class AppViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AppViewModel::class.java)) {
            return AppViewModel(container) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
