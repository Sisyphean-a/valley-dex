package com.example.stardewoffline.core.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.example.stardewoffline.core.datastore.AppPreferences

val LocalAppPreferences = staticCompositionLocalOf { AppPreferences() }
