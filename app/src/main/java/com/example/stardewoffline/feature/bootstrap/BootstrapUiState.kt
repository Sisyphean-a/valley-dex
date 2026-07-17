package com.example.stardewoffline.feature.bootstrap

import com.example.stardewoffline.core.common.AppError
import com.example.stardewoffline.core.model.DataPackageInfo

sealed interface BootstrapUiState {
    data object Loading : BootstrapUiState
    data class Installing(val message: String) : BootstrapUiState
    data object NeedDataPackage : BootstrapUiState
    data class Ready(val packageInfo: DataPackageInfo) : BootstrapUiState
    data class Error(val error: AppError) : BootstrapUiState
}
