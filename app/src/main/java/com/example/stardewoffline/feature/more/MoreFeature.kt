package com.example.stardewoffline.feature.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.automirrored.filled.TextSnippet
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun MoreRoute(
    onHistory: () -> Unit,
    onDataManagement: () -> Unit,
    onSettings: () -> Unit,
    onAbout: () -> Unit,
    onLicenses: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(vertical = 16.dp)) {
        Text("更多", modifier = Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.headlineSmall)
        MoreItem("浏览历史", Icons.Filled.History, onHistory)
        MoreItem("数据管理", Icons.Filled.Storage, onDataManagement)
        MoreItem("设置", Icons.Filled.Settings, onSettings)
        MoreItem("关于", Icons.Filled.Info, onAbout)
        MoreItem("开源许可", Icons.AutoMirrored.Filled.TextSnippet, onLicenses)
    }
}

@Composable
private fun MoreItem(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        leadingContent = { Icon(icon, contentDescription = null) },
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    )
}
