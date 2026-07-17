package com.example.stardewoffline.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import com.example.stardewoffline.feature.bootstrap.BootstrapRoute

@Composable
fun StardewOfflineRoot() {
    val ready = rememberSaveable { mutableStateOf(false) }
    if (ready.value) AppNavHost() else BootstrapRoute(onReady = { ready.value = true })
}
