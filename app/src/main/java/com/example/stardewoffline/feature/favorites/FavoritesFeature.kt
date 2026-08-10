package com.example.stardewoffline.feature.favorites

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.stardewoffline.core.common.getOrNull
import com.example.stardewoffline.core.database.user.FavoriteEntity
import com.example.stardewoffline.core.datastore.AppPreferencesRepository
import com.example.stardewoffline.core.model.WikiEntrySummary
import com.example.stardewoffline.core.ui.component.WikiEntryListItem
import com.example.stardewoffline.data.ContentRepository
import com.example.stardewoffline.data.UserDataRepository
import com.example.stardewoffline.data.wiki.WikiCatalogue
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

data class FavoriteRow(val record: FavoriteEntity, val entry: WikiEntrySummary?)

@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val user: UserDataRepository,
    private val catalogue: WikiCatalogue,
    private val content: ContentRepository,
    private val preferences: AppPreferencesRepository,
) : ViewModel() {
    private val mutableRows = MutableStateFlow<List<FavoriteRow>>(emptyList())
    private val mutableRoot = MutableStateFlow<File?>(null)
    val rows = mutableRows.asStateFlow()
    val root = mutableRoot.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                user.favorites(),
                preferences.preferences.map { it.activePackageId }.distinctUntilChanged(),
            ) { favorites, _ -> favorites }.collectLatest { favorites ->
                mutableRoot.value = content.packageRoot()
                val entries = catalogue.summaries(favorites.map(FavoriteEntity::entityId)).getOrNull().orEmpty()
                mutableRows.value = favorites.map { FavoriteRow(it, entries[it.entityId]) }
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
    val knownTypes = rows.mapNotNull { it.entry?.categoryLabel }.distinct()
    val visible = rows.filter { row ->
        val title = row.entry?.title.orEmpty()
        (row.entry == null || title.contains(query, ignoreCase = true)) && (types.isEmpty() || row.entry?.categoryLabel in types)
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        item {
            Column(Modifier.fillMaxWidth().background(Color(0xFF163F37)).padding(start = 20.dp, top = 28.dp, end = 20.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("我的收藏", style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.ExtraBold)
                Text("${rows.size} 条已保存资料", style = MaterialTheme.typography.bodySmall, color = Color(0xFFC7D8D1))
                OutlinedTextField(
                    query,
                    { query = it },
                    Modifier.fillMaxWidth(),
                    label = { Text("筛选收藏") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
            }
        }
        if (knownTypes.isNotEmpty()) item {
            LazyRow(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                items(knownTypes) { type -> FilterChip(type in types, { types = types.toMutableSet().apply { if (!add(type)) remove(type) } }, { Text(type) }) }
            }
        }
        if (visible.isEmpty()) item { Text("还没有符合条件的收藏", Modifier.padding(horizontal = 20.dp, vertical = 24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(visible, key = { it.record.entityId }) { row ->
            row.entry?.let { entry -> WikiEntryListItem(entry, root, Modifier.padding(horizontal = 16.dp), onClick = { onDetail(entry.id) }) }
                ?: MissingFavorite(row.record.entityId, onRemove)
        }
    }
}

@Composable
private fun MissingFavorite(id: String, onRemove: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = androidx.compose.ui.Alignment.End) {
        Text("当前数据包中已不存在", modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleSmall)
        IconButton(onClick = { onRemove(id) }) { Icon(Icons.Filled.Delete, "删除收藏") }
    }
}
