package com.example.stardewoffline.feature.bootstrap

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.stardewoffline.core.common.AppError
import com.example.stardewoffline.core.common.AppResult
import com.example.stardewoffline.data.DataPackageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.InputStream
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class BootstrapViewModel @Inject constructor(
    private val dataPackages: DataPackageRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow<BootstrapUiState>(BootstrapUiState.Loading)
    val state: StateFlow<BootstrapUiState> = mutableState.asStateFlow()

    init {
        initialize()
    }

    fun initialize() = viewModelScope.launch {
        mutableState.value = BootstrapUiState.Loading
        when (val active = dataPackages.openActive()) {
            is AppResult.Success -> mutableState.value = BootstrapUiState.Ready(active.value)
            is AppResult.Failure -> installDefaultOrRequestImport(active.error)
        }
    }

    fun import(input: InputStream) = viewModelScope.launch {
        mutableState.value = BootstrapUiState.Installing("正在导入数据包")
        updateAfterImport { dataPackages.import(input, ::setInstallStage) }
    }

    fun reportImportAccessError() {
        mutableState.value = BootstrapUiState.Error(AppError.ImportCancelled)
    }

    private suspend fun installDefaultOrRequestImport(openError: AppError) {
        if (openError != AppError.NoDataPackage || !dataPackages.hasDefaultPackage()) {
            mutableState.value = if (openError == AppError.NoDataPackage) BootstrapUiState.NeedDataPackage else BootstrapUiState.Error(openError)
            return
        }
        mutableState.value = BootstrapUiState.Installing("正在安装内置数据包")
        updateAfterImport { dataPackages.installDefault(::setInstallStage) }
    }

    private suspend fun updateAfterImport(action: suspend () -> AppResult<com.example.stardewoffline.core.model.DataPackageInfo>) {
        when (val result = action()) {
            is AppResult.Success -> mutableState.value = BootstrapUiState.Ready(result.value)
            is AppResult.Failure -> mutableState.value = BootstrapUiState.Error(result.error)
        }
    }

    private fun setInstallStage(stage: com.example.stardewoffline.core.datapackage.PackageInstallStage) {
        mutableState.value = BootstrapUiState.Installing(stage.message)
    }
}
