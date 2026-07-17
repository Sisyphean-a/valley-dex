package com.example.stardewoffline.feature.bootstrap

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun BootstrapRoute(
    viewModel: BootstrapViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { selected ->
            val input = context.contentResolver.openInputStream(selected)
            if (input == null) viewModel.reportImportAccessError() else viewModel.import(input)
        }
    }
    BootstrapScreen(
        state = state,
        onChoosePackage = { launcher.launch(arrayOf("application/octet-stream", "application/zip", "application/x-zip-compressed")) },
        onRetry = viewModel::initialize,
    )
}
