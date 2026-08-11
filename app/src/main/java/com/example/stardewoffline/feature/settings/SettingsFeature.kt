package com.example.stardewoffline.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.stardewoffline.core.datastore.AppPreferences
import com.example.stardewoffline.core.datastore.AppPreferencesRepository
import com.example.stardewoffline.data.UserDataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: AppPreferencesRepository,
    private val user: UserDataRepository,
) : ViewModel() {
    val settings = preferences.preferences.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppPreferences())
    fun setTheme(value: String) = viewModelScope.launch { preferences.setThemeMode(value) }
    fun setDynamic(value: Boolean) = viewModelScope.launch { preferences.setDynamicColorEnabled(value) }
    fun setEnglish(value: Boolean) = viewModelScope.launch { preferences.setShowEnglishName(value) }
    fun setTechnical(value: Boolean) = viewModelScope.launch { preferences.setShowTechnicalFields(value) }
    fun setSearchHistory(value: Boolean) = viewModelScope.launch { preferences.setSearchHistoryEnabled(value) }
    fun clearHistory() = viewModelScope.launch { user.clearHistory() }
    fun clearSearches() = viewModelScope.launch { user.clearSearches() }
}

@Composable
fun SettingsRoute(onBack: () -> Unit = {}, viewModel: SettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsState()
    SettingsScreen(settings, onBack, viewModel::setTheme, viewModel::setDynamic, viewModel::setEnglish, viewModel::setTechnical, viewModel::setSearchHistory, viewModel::clearHistory, viewModel::clearSearches)
}

@Composable
private fun SettingsScreen(
    settings: AppPreferences,
    onBack: () -> Unit,
    onTheme: (String) -> Unit,
    onDynamic: (Boolean) -> Unit,
    onEnglish: (Boolean) -> Unit,
    onTechnical: (Boolean) -> Unit,
    onSearchHistory: (Boolean) -> Unit,
    onClearHistory: () -> Unit,
    onClearSearches: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState())) {
        Row(
            Modifier.fillMaxWidth().background(Color(0xFF163F37)).padding(start = 4.dp, top = 18.dp, end = 20.dp, bottom = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = Color.White) }
            Column(Modifier.padding(start = 4.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("设置", style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.ExtraBold)
                Text("调整图鉴显示和本地记录方式", style = MaterialTheme.typography.bodySmall, color = Color(0xFFC7D8D1))
            }
        }
        SettingsSection("外观") {
            ChoiceRow("主题", listOf("跟随系统" to "system", "浅色" to "light", "深色" to "dark"), settings.themeMode, onTheme)
            SettingSwitch("使用动态配色", "跟随设备壁纸生成配色", settings.dynamicColorEnabled, onDynamic)
        }
        SettingsSection("内容显示") {
            SettingSwitch("显示英文名称", "在中文名下显示英文原名", settings.showEnglishName, onEnglish)
            SettingSwitch("显示技术字段", "展示高级数据与原始条件", settings.showTechnicalFields, onTechnical)
        }
        SettingsSection("隐私与记录") {
            SettingSwitch("记录搜索历史", "搜索词仅保存在本机", settings.searchHistoryEnabled, onSearchHistory)
            ActionRow("清除浏览历史", onClearHistory)
            ActionRow("清除搜索历史", onClearSearches)
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, top = 20.dp, end = 16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.ExtraBold)
        Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChoiceRow(title: String, options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            options.forEach { (label, value) -> FilterChip(selected = selected == value, onClick = { onSelect(value) }, label = { Text(label) }) }
        }
    }
}

@Composable
private fun SettingSwitch(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ActionRow(title: String, onClick: () -> Unit) {
    FilterChip(selected = false, onClick = onClick, label = { Text(title) })
}
