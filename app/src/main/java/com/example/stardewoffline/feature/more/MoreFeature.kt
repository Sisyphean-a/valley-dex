package com.example.stardewoffline.feature.more

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TextSnippet
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val MoreGreen = Color(0xFF163F37)

@Composable
fun MoreRoute(
    onHistory: () -> Unit,
    onDataManagement: () -> Unit,
    onSettings: () -> Unit,
    onAbout: () -> Unit,
    onLicenses: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState())) {
        Surface(color = MoreGreen, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(start = 20.dp, top = 32.dp, end = 20.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("更多工具", style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = FontWeight.ExtraBold)
                Text("管理离线数据与个人阅读记录", style = MaterialTheme.typography.bodyMedium, color = Color(0xFFC7D8D1))
            }
        }
        Text("个人资料", Modifier.padding(start = 20.dp, top = 24.dp, end = 20.dp, bottom = 8.dp), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.ExtraBold)
        MoreItem("浏览历史", "最近看过的图鉴条目", Icons.Filled.History, onHistory)
        Text("应用设置", Modifier.padding(start = 20.dp, top = 24.dp, end = 20.dp, bottom = 8.dp), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.ExtraBold)
        MoreItem("数据管理", "导入、校验与切换数据包", Icons.Filled.Storage, onDataManagement)
        MoreItem("设置", "显示、搜索和隐私选项", Icons.Filled.Settings, onSettings)
        MoreItem("关于", "版本、数据来源与说明", Icons.Filled.Info, onAbout)
        MoreItem("开源许可", "本应用使用的开源组件", Icons.AutoMirrored.Filled.TextSnippet, onLicenses)
        Text("VALLEY INDEX · 完全离线", Modifier.padding(20.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MoreItem(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        shadowElevation = 1.dp,
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = Color(0xFFE1EEE7), shape = MaterialTheme.shapes.small, modifier = Modifier.size(42.dp)) {
                Icon(icon, contentDescription = null, tint = MoreGreen, modifier = Modifier.padding(10.dp))
            }
            Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
