package com.example.stardewoffline.feature.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AboutRoute(onBack: () -> Unit) = StaticPage("关于", ABOUT_TEXT, onBack)

@Composable
fun LicensesRoute(onBack: () -> Unit) = StaticPage("开源许可", LICENSE_TEXT, onBack)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun StaticPage(title: String, content: String, onBack: () -> Unit) {
    Scaffold(topBar = {
        TopAppBar(title = { Text(title) }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
        })
    }) { padding ->
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(content, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

private const val ABOUT_TEXT = """
星露谷离线图鉴是一个非官方离线查询工具，与 ConcernedApe、发行商和 Wiki 没有从属关系。

游戏名称、角色、数据和素材的权利归原权利人所有。数据包应由用户自己的游戏资源生成；本应用不爬取 Wiki、不读取存档、不联网，也不上传任何数据。

应用使用本地 SQLite 内容库、Room 用户数据和 DataStore 设置。数据生成器版本与游戏版本可在数据管理页查看。
"""

private const val LICENSE_TEXT = """
本应用使用 Kotlin、AndroidX、Jetpack Compose、Material 3、Hilt、Room、DataStore、kotlinx.serialization、Coil 与 Gradle。各依赖的许可证由其发行方提供，并可通过 Gradle 依赖元数据追溯。

星露谷物语相关名称、角色、数据和素材不属于本项目许可证范围，权利归原权利人所有。
"""
