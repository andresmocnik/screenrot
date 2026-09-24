package com.screenrot.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.screenrot.app.CharacterUpdatePipeline
import com.screenrot.app.data.CharacterStateStore
import com.screenrot.app.screentime.UsageStatsRepository
import com.screenrot.core.CharacterState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val pipeline = CharacterUpdatePipeline(application)
    private val usageRepo = UsageStatsRepository(application)
    private val store = CharacterStateStore(application)

    private val _uiState = MutableStateFlow(
        UiState(
            hasPermission = usageRepo.hasUsageAccessPermission(),
            character = CharacterState.PRISTINE,
            topAppsSummary = "",
            isLoading = false
        )
    )
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    data class UiState(
        val hasPermission: Boolean,
        val character: CharacterState,
        val topAppsSummary: String,
        val isLoading: Boolean
    )

    /** Call from onResume — covers "user just came back from granting the permission". */
    fun onScreenResumed() {
        val granted = usageRepo.hasUsageAccessPermission()
        _uiState.value = _uiState.value.copy(hasPermission = granted)
        if (granted) refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            when (val result = pipeline.refresh()) {
                is CharacterUpdatePipeline.Result.Updated -> {
                    val summary = store.topAppsSummary.first()
                    _uiState.value = _uiState.value.copy(
                        character = result.state,
                        topAppsSummary = summary,
                        hasPermission = true,
                        isLoading = false
                    )
                }
                CharacterUpdatePipeline.Result.PermissionMissing -> {
                    _uiState.value = _uiState.value.copy(hasPermission = false, isLoading = false)
                }
            }
        }
    }
}
