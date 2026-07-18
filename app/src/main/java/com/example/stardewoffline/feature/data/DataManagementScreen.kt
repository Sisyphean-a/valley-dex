package com.example.stardewoffline.feature.data

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun DataManagementScreen(
    state: DataManagementUiState,
    onBack: () -> Unit,
    onImport: () -> Unit,
    onVerify: () -> Unit,
    onRollback: () -> Unit,
    onDeletePrevious: () -> Unit,
    onExport: () -> Unit,
) {
    Scaffold(topBar = { DataTopBar(onBack) }) { padding ->
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("数据管理", style = MaterialTheme.typography.headlineSmall)
            state.info?.let { DataInfo(it) } ?: Text(state.error ?: "正在读取数据版本")
            state.busyMessage?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
            state.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            Button(onClick = onImport, modifier = Modifier.fillMaxWidth(), enabled = state.busyMessage == null) { Text("导入新数据包") }
            OutlinedButton(onClick = onVerify, modifier = Modifier.fillMaxWidth(), enabled = state.busyMessage == null) { Text("验证当前数据") }
            OutlinedButton(onClick = onRollback, modifier = Modifier.fillMaxWidth(), enabled = state.busyMessage == null) { Text("回滚上一数据包") }
            OutlinedButton(onClick = onDeletePrevious, modifier = Modifier.fillMaxWidth(), enabled = state.busyMessage == null) { Text("删除旧数据包") }
            OutlinedButton(onClick = onExport, modifier = Modifier.fillMaxWidth()) { Text("导出诊断信息") }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun DataTopBar(onBack: () -> Unit) {
    TopAppBar(title = { Text("数据管理") }, navigationIcon = {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
    })
}

@Composable
private fun DataInfo(info: com.example.stardewoffline.core.model.DataPackageInfo) {
    val meta = info.buildMeta
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("游戏版本：${meta.gameVersion}")
        Text("数据生成时间：${meta.generatedAt}")
        Text("数据库 Schema：${meta.schemaVersion}")
        Text("Builder：${meta.builderVersion}")
        Text("实体总数：${meta.entityCount}")
        Text("缺少翻译：${info.manifest.content.missingTranslations}")
        Text("数据包：${info.id.take(12)}")
    }
}
