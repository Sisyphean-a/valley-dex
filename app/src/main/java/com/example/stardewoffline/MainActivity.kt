package com.example.stardewoffline

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.stardewoffline.core.ui.theme.StardewOfflineTheme
import com.example.stardewoffline.navigation.StardewOfflineRoot
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            StardewOfflineTheme {
                StardewOfflineRoot()
            }
        }
    }
}
