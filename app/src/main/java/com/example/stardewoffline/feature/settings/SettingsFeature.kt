package com.example.stardewoffline.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
    fun setLayout(value: String) = viewModelScope.launch { preferences.setListLayoutMode(value) }
    fun clearHistory() = viewModelScope.launch { user.clearHistory() }
    fun clearSearches() = viewModelScope.launch { user.clearSearches() }
}

@Composable
fun SettingsRoute(viewModel: SettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsState()
    SettingsScreen(settings, viewModel::setTheme, viewModel::setDynamic, viewModel::setEnglish, viewModel::setTechnical, viewModel::setSearchHistory, viewModel::setLayout, viewModel::clearHistory, viewModel::clearSearches)
}

@Composable
private fun SettingsScreen(
    settings: AppPreferences,
    onTheme: (String) -> Unit,
    onDynamic: (Boolean) -> Unit,
    onEnglish: (Boolean) -> Unit,
    onTechnical: (Boolean) -> Unit,
    onSearchHistory: (Boolean) -> Unit,
    onLayout: (String) -> Unit,
    onClearHistory: () -> Unit,
    onClearSearches: () -> Unit,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("设置", style = MaterialTheme.typography.headlineSmall)
        ChoiceRow("主题", listOf("跟随系统" to "system", "浅色" to "light", "深色" to "dark"), settings.themeMode, onTheme)
        SettingSwitch("使用动态配色", settings.dynamicColorEnabled, onDynamic)
        SettingSwitch("显示英文名称", settings.showEnglishName, onEnglish)
        SettingSwitch("显示技术字段", settings.showTechnicalFields, onTechnical)
        SettingSwitch("记录搜索历史", settings.searchHistoryEnabled, onSearchHistory)
        ChoiceRow("列表布局", listOf("列表" to "list", "紧凑" to "compact"), settings.listLayoutMode, onLayout)
        HorizontalDivider()
        ActionRow("清除浏览历史", onClearHistory)
        ActionRow("清除搜索历史", onClearSearches)
    }
}

@Composable
private fun ChoiceRow(title: String, options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        options.forEach { (label, value) -> FilterChip(selected = selected == value, onClick = { onSelect(value) }, label = { Text(label) }) }
    }
}

@Composable
private fun SettingSwitch(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ActionRow(title: String, onClick: () -> Unit) {
    FilterChip(selected = false, onClick = onClick, label = { Text(title) })
}
