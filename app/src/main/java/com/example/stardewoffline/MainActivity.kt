package com.example.stardewoffline

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import com.example.stardewoffline.core.datastore.AppPreferences
import com.example.stardewoffline.core.datastore.AppPreferencesRepository
import com.example.stardewoffline.core.ui.LocalAppPreferences
import com.example.stardewoffline.core.ui.theme.StardewOfflineTheme
import com.example.stardewoffline.navigation.StardewOfflineRoot
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var preferences: AppPreferencesRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by preferences.preferences.collectAsState(initial = AppPreferences())
            val darkTheme = when (settings.themeMode) {
                "light" -> false
                "dark" -> true
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }
            CompositionLocalProvider(LocalAppPreferences provides settings) {
                StardewOfflineTheme(darkTheme = darkTheme, dynamicColor = settings.dynamicColorEnabled) {
                    StardewOfflineRoot()
                }
            }
        }
    }
}
