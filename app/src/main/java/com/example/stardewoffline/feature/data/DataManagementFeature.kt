package com.example.stardewoffline.feature.data

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.stardewoffline.core.common.AppResult
import com.example.stardewoffline.core.model.DataPackageInfo
import com.example.stardewoffline.data.DataPackageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class DataManagementViewModel @Inject constructor(private val packages: DataPackageRepository) : ViewModel() {
    private val mutableInfo = MutableStateFlow<DataPackageInfo?>(null); private val mutableMessage = MutableStateFlow<String?>(null)
    val info = mutableInfo.asStateFlow(); val message = mutableMessage.asStateFlow()
    init { refresh() }
    fun refresh() = viewModelScope.launch { mutableInfo.value = (packages.openActive() as? AppResult.Success)?.value }
    fun verify() = viewModelScope.launch { val result = packages.verifyActive(); mutableMessage.value = if (result is AppResult.Success) "验证通过" else "验证失败" }
    fun rollback() = viewModelScope.launch { val result = packages.rollback(); mutableMessage.value = if (result is AppResult.Success) "已回滚" else "没有可回滚的数据包"; refresh() }
}

@Composable
fun DataManagementRoute(viewModel: DataManagementViewModel = hiltViewModel()) {
    val info by viewModel.info.collectAsState(); val message by viewModel.message.collectAsState()
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("数据管理"); info?.let { Text("游戏 ${it.buildMeta.gameVersion}\nSchema ${it.buildMeta.schemaVersion}\n${it.buildMeta.entityCount} 个条目\n${it.id.take(12)}") }
        Button(onClick = viewModel::verify) { Text("验证当前数据") }; Button(onClick = viewModel::rollback) { Text("回滚上一数据包") }; message?.let { Text(it) }
    }
}
