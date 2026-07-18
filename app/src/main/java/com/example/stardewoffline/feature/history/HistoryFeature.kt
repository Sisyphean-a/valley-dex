package com.example.stardewoffline.feature.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.stardewoffline.core.common.getOrNull
import com.example.stardewoffline.core.database.user.HistoryEntity
import com.example.stardewoffline.core.model.EntitySummary
import com.example.stardewoffline.data.ContentRepository
import com.example.stardewoffline.data.UserDataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HistoryRow(val record: HistoryEntity, val summary: EntitySummary?)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val user: UserDataRepository,
    private val content: ContentRepository,
) : ViewModel() {
    private val mutableRows = MutableStateFlow<List<HistoryRow>>(emptyList())
    private val mutableRoot = MutableStateFlow<File?>(null)
    val rows = mutableRows.asStateFlow()
    val root = mutableRoot.asStateFlow()

    init {
        viewModelScope.launch {
            mutableRoot.value = content.packageRoot()
            user.history().collect { records ->
                val entities = content.summaries(records.map(HistoryEntity::entityId)).getOrNull().orEmpty()
                mutableRows.value = records.map { HistoryRow(it, entities[it.entityId]) }
            }
        }
    }

    fun delete(id: String) = viewModelScope.launch { user.deleteHistory(id) }
    fun clear() = viewModelScope.launch { user.clearHistory() }
}

@Composable
fun HistoryRoute(onBack: () -> Unit, onDetail: (String) -> Unit, viewModel: HistoryViewModel = hiltViewModel()) {
    val rows by viewModel.rows.collectAsState()
    val root by viewModel.root.collectAsState()
    HistoryScreen(rows, root, onBack, onDetail, viewModel::delete, viewModel::clear)
}

@Composable
private fun HistoryScreen(
    rows: List<HistoryRow>,
    root: File?,
    onBack: () -> Unit,
    onDetail: (String) -> Unit,
    onDelete: (String) -> Unit,
    onClear: () -> Unit,
) {
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    Scaffold(topBar = { HistoryTopBar(onBack, rows.isNotEmpty()) { confirmClear = true } }) { padding ->
        if (rows.isEmpty()) EmptyHistory(Modifier.padding(padding)) else LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
            items(rows, key = { it.record.entityId }) { row -> HistoryItem(row, root, onDetail, onDelete) }
        }
    }
    if (confirmClear) ClearDialog(onClear) { confirmClear = false }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun HistoryTopBar(onBack: () -> Unit, canClear: Boolean, onClear: () -> Unit) {
    TopAppBar(
        title = { Text("浏览历史") },
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
        actions = { if (canClear) IconButton(onClick = onClear) { Icon(Icons.Filled.Delete, "清空历史") } },
    )
}

@Composable
private fun HistoryItem(row: HistoryRow, root: File?, onDetail: (String) -> Unit, onDelete: (String) -> Unit) {
    val name = row.summary?.nameZh ?: "当前数据包中已不存在"
    Column(
        Modifier.fillMaxWidth().clickable(enabled = row.summary != null) { onDetail(row.record.entityId) }.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(name, style = MaterialTheme.typography.titleMedium)
        Text("${displayTime(row.record.lastViewedAt)} · 浏览 ${row.record.viewCount} 次", style = MaterialTheme.typography.bodySmall)
        if (row.summary == null) Text(row.record.entityId, style = MaterialTheme.typography.labelSmall)
        IconButton(onClick = { onDelete(row.record.entityId) }) { Icon(Icons.Filled.Delete, "删除历史记录") }
    }
}

@Composable
private fun EmptyHistory(modifier: Modifier) {
    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
        Text("还没有浏览历史", style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun ClearDialog(onClear: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("清空浏览历史") }, text = { Text("所有浏览记录都会被删除。") }, confirmButton = {
        TextButton(onClick = { onClear(); onDismiss() }) { Text("清空") }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

private fun displayTime(time: Long): String = Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).format(TIME_FORMAT)
private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
