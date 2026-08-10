package com.example.stardewoffline.feature.data

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = { DataTopBar(onBack) }) { padding ->
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            state.info?.let { DataInfo(it) } ?: StatusSurface(state.error ?: "正在读取数据版本", state.error != null)
            state.busyMessage?.let { StatusSurface(it, false) }
            state.message?.let { StatusSurface(it, false) }
            Text("数据包操作", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.ExtraBold)
            Button(onClick = onImport, modifier = Modifier.fillMaxWidth(), enabled = state.busyMessage == null) { Text("导入新数据包") }
            OutlinedButton(onClick = onVerify, modifier = Modifier.fillMaxWidth(), enabled = state.busyMessage == null) { Text("验证当前数据") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onRollback, modifier = Modifier.weight(1f), enabled = state.busyMessage == null) { Text("回滚数据") }
                OutlinedButton(onClick = onDeletePrevious, modifier = Modifier.weight(1f), enabled = state.busyMessage == null) { Text("删除旧包") }
            }
            OutlinedButton(onClick = onExport, modifier = Modifier.fillMaxWidth()) { Text("导出诊断信息") }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun DataTopBar(onBack: () -> Unit) {
    TopAppBar(
        title = { Column { Text("数据管理"); Text("离线内容包与版本", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
    )
}

@Composable
private fun DataInfo(info: com.example.stardewoffline.core.model.DataPackageInfo) {
    val meta = info.buildMeta
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = Color(0xFF163F37), shadowElevation = 2.dp) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("当前数据已就绪", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.ExtraBold)
                    Text("游戏 ${meta.gameVersion} · Schema ${meta.schemaVersion}", style = MaterialTheme.typography.bodySmall, color = Color(0xFFC7D8D1))
                }
                Text("${meta.entityCount}", style = MaterialTheme.typography.headlineSmall, color = Color(0xFFE1AD4B), fontWeight = FontWeight.ExtraBold)
            }
            HorizontalDivider(color = Color(0xFF3C625A))
            DataRow("数据生成时间", meta.generatedAt)
            DataRow("构建器", meta.builderVersion)
            DataRow("缺少翻译", info.manifest.content.missingTranslations.toString())
            DataRow("数据包", info.id.take(12))
        }
    }
}

@Composable
private fun DataRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, modifier = Modifier.weight(0.38f), style = MaterialTheme.typography.labelMedium, color = Color(0xFFC7D8D1))
        Text(value, modifier = Modifier.weight(0.62f), style = MaterialTheme.typography.bodySmall, color = Color.White)
    }
}

@Composable
private fun StatusSurface(message: String, error: Boolean) {
    Surface(Modifier.fillMaxWidth(), color = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.medium) {
        Text(message, Modifier.padding(14.dp), color = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface)
    }
}
