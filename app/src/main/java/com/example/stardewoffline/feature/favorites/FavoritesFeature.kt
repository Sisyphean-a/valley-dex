package com.example.stardewoffline.feature.favorites

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.stardewoffline.core.common.getOrNull
import com.example.stardewoffline.core.database.user.FavoriteEntity
import com.example.stardewoffline.core.model.EntitySummary
import com.example.stardewoffline.core.ui.component.EntityListItem
import com.example.stardewoffline.data.ContentRepository
import com.example.stardewoffline.data.UserDataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class FavoriteRow(val record: FavoriteEntity, val summary: EntitySummary?)

@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val user: UserDataRepository,
    private val content: ContentRepository,
) : ViewModel() {
    private val mutableRows = MutableStateFlow<List<FavoriteRow>>(emptyList())
    private val mutableRoot = MutableStateFlow<File?>(null)
    val rows = mutableRows.asStateFlow()
    val root = mutableRoot.asStateFlow()

    init {
        viewModelScope.launch {
            mutableRoot.value = content.packageRoot()
            user.favorites().collect { favorites ->
                val summaries = content.summaries(favorites.map(FavoriteEntity::entityId)).getOrNull().orEmpty()
                mutableRows.value = favorites.map { FavoriteRow(it, summaries[it.entityId]) }
            }
        }
    }

    fun remove(id: String) = viewModelScope.launch { user.toggleFavorite(id, false) }
}

@Composable
fun FavoritesRoute(onDetail: (String) -> Unit, viewModel: FavoritesViewModel = hiltViewModel()) {
    val rows by viewModel.rows.collectAsState()
    val root by viewModel.root.collectAsState()
    FavoritesScreen(rows, root, onDetail, viewModel::remove)
}

@Composable
private fun FavoritesScreen(rows: List<FavoriteRow>, root: File?, onDetail: (String) -> Unit, onRemove: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    var types by remember { mutableStateOf(setOf<String>()) }
    val knownTypes = rows.mapNotNull { it.summary?.entityType }.distinct()
    val visible = rows.filter { row ->
        val name = row.summary?.nameZh ?: row.record.entityId
        name.contains(query, ignoreCase = true) && (types.isEmpty() || row.summary?.entityType in types)
    }
    LazyColumn(Modifier.fillMaxSize()) {
        item { OutlinedTextField(query, { query = it }, Modifier.padding(16.dp), label = { Text("筛选收藏") }, singleLine = true) }
        item { knownTypes.forEach { type -> FilterChip(type in types, { types = types.toMutableSet().apply { if (!add(type)) remove(type) } }, { Text(type) }) } }
        items(visible, key = { it.record.entityId }) { row ->
            val summary = row.summary
            if (summary != null) EntityListItem(summary, root) { onDetail(summary.id) } else MissingFavorite(row.record.entityId, onRemove)
        }
    }
}

@Composable
private fun MissingFavorite(id: String, onRemove: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = androidx.compose.ui.Alignment.End) {
        Text("当前数据包中已不存在", modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleSmall)
        Text(id, modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodySmall)
        IconButton(onClick = { onRemove(id) }) { Icon(Icons.Filled.Delete, "删除收藏") }
    }
}
