package com.team2207.roboroute.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.team2207.roboroute.datastore.Action
import com.team2207.roboroute.datastore.ActionRepository
import com.team2207.roboroute.datastore.AppData
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ActionRepository(application)

    val appData: StateFlow<AppData> = repository.appDataFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AppData.getDefaultInstance()
    )

    fun saveAction(action: Action) {
        viewModelScope.launch {
            repository.addAction(action)
        }
    }
}
