package com.colorbounce.baby

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * Loads persisted settings off the UI thread before the first frame.
 * Avoids blocking [MainActivity.onCreate] on first-run DataStore file creation.
 */
class MainViewModel(settingsRepository: SettingsRepository) : ViewModel() {
    val settings: StateFlow<AppSettings> = settingsRepository.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = AppSettings()
    )
}
