package com.example.stardewoffline.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.stardewoffline.core.datastore.AppPreferencesRepository
import com.example.stardewoffline.feature.bootstrap.BootstrapRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class StardewOfflineRootViewModel @Inject constructor(
    preferences: AppPreferencesRepository,
) : ViewModel() {
    val activePackageId = preferences.preferences
        .map { it.activePackageId }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)
}

@Composable
fun StardewOfflineRoot(viewModel: StardewOfflineRootViewModel = androidx.hilt.navigation.compose.hiltViewModel()) {
    val activePackageId by viewModel.activePackageId.collectAsState()
    var readyPackageId by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(activePackageId) {
        if (activePackageId == null) readyPackageId = null
    }
    if (activePackageId != null && activePackageId == readyPackageId) {
        AppNavHost()
    } else {
        BootstrapRoute(onReady = { readyPackageId = it })
    }
}
