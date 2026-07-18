package com.example.stardewoffline.feature.data

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.stardewoffline.core.common.getOrNull
import com.example.stardewoffline.core.common.AppResult
import com.example.stardewoffline.core.datapackage.PackageInstallStage
import com.example.stardewoffline.core.model.DataPackageInfo
import com.example.stardewoffline.data.DataPackageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class DataManagementUiState(
    val info: DataPackageInfo? = null,
    val busyMessage: String? = null,
    val message: String? = null,
    val error: String? = null,
)

@HiltViewModel
class DataManagementViewModel @Inject constructor(
    private val packages: DataPackageRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(DataManagementUiState())
    val state = mutableState.asStateFlow()

    init { refresh() }

    fun refresh() = viewModelScope.launch { updateResult(packages.openActive(), null) }

    fun import(input: InputStream) = viewModelScope.launch {
        mutableState.value = mutableState.value.copy(busyMessage = PackageInstallStage.Copying.message, error = null)
        val result = packages.import(input) { stage -> mutableState.value = mutableState.value.copy(busyMessage = stage.message) }
        updateResult(result, "已启用新数据包")
    }

    fun verify() = viewModelScope.launch { updateResult(packages.verifyActive(), "验证当前数据") }

    fun rollback() = viewModelScope.launch { updateResult(packages.rollback(), "已回滚到上一数据包") }

    fun deletePrevious() = viewModelScope.launch {
        val success = packages.deletePreviousPackage().getOrNull() != null
        mutableState.value = mutableState.value.copy(message = if (success) "已删除旧数据包" else "删除旧数据包失败")
    }

    fun exportDiagnostic(output: OutputStream) = viewModelScope.launch {
        output.writer().use { it.write(diagnosticJson(mutableState.value.info)) }
        mutableState.value = mutableState.value.copy(message = "诊断信息已导出")
    }

    private fun updateResult(result: AppResult<DataPackageInfo>, successMessage: String?) {
        mutableState.value = when (result) {
            is AppResult.Success -> mutableState.value.copy(info = result.value, busyMessage = null, message = successMessage, error = null)
            is AppResult.Failure -> mutableState.value.copy(busyMessage = null, message = null, error = result.error.message)
        }
    }
}

@Composable
fun DataManagementRoute(onBack: () -> Unit, viewModel: DataManagementViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { context.contentResolver.openInputStream(it)?.let(viewModel::import) }
    }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { context.contentResolver.openOutputStream(it)?.let(viewModel::exportDiagnostic) }
    }
    DataManagementScreen(
        state = state,
        onBack = onBack,
        onImport = { importer.launch(MIME_TYPES) },
        onVerify = viewModel::verify,
        onRollback = viewModel::rollback,
        onDeletePrevious = viewModel::deletePrevious,
        onExport = { exporter.launch("stardew-offline-diagnostic.json") },
    )
}

private fun diagnosticJson(info: DataPackageInfo?): String = buildJsonObject {
    put("schemaVersion", info?.buildMeta?.schemaVersion ?: 0)
    put("gameVersion", info?.buildMeta?.gameVersion.orEmpty())
    put("builderVersion", info?.buildMeta?.builderVersion.orEmpty())
    put("entityCount", info?.buildMeta?.entityCount ?: 0)
    put("databaseHashPrefix", info?.id?.take(12).orEmpty())
}.toString()

private val MIME_TYPES = arrayOf("application/octet-stream", "application/zip", "application/x-zip-compressed")
