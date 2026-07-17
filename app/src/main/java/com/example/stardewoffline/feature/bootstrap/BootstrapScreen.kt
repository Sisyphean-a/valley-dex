package com.example.stardewoffline.feature.bootstrap

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun BootstrapScreen(
    state: BootstrapUiState,
    onChoosePackage: () -> Unit,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (state) {
            BootstrapUiState.Loading -> LoadingContent("正在检查离线数据")
            is BootstrapUiState.Installing -> LoadingContent(state.message)
            BootstrapUiState.NeedDataPackage -> ImportContent(onChoosePackage)
            is BootstrapUiState.Error -> ErrorContent(state.error.message, onChoosePackage, onRetry)
            is BootstrapUiState.Ready -> ReadyContent(state)
        }
    }
}

@Composable
private fun LoadingContent(message: String) {
    CircularProgressIndicator()
    Spacer(Modifier.height(20.dp))
    Text(message, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun ImportContent(onChoosePackage: () -> Unit) {
    Text("星露谷离线图鉴", style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(12.dp))
    Text("请选择由游戏资源生成的 .svdata 数据包。应用不会联网，也不会读取你的存档。")
    Spacer(Modifier.height(24.dp))
    Button(onClick = onChoosePackage, modifier = Modifier.fillMaxWidth()) {
        Text("选择数据包")
    }
}

@Composable
private fun ErrorContent(message: String, onChoosePackage: () -> Unit, onRetry: () -> Unit) {
    Text("无法准备数据", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.error)
    Spacer(Modifier.height(12.dp))
    Text(message)
    Spacer(Modifier.height(24.dp))
    Button(onClick = onChoosePackage, modifier = Modifier.fillMaxWidth()) { Text("选择其他数据包") }
    Spacer(Modifier.height(8.dp))
    OutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text("重试") }
}

@Composable
private fun ReadyContent(state: BootstrapUiState.Ready) {
    Text("数据已就绪", style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(12.dp))
    Text("${state.packageInfo.buildMeta.entityCount} 个条目 · 游戏 ${state.packageInfo.buildMeta.gameVersion}")
}
