package com.example.stardewoffline.feature.type

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.stardewoffline.core.common.AppResult
import com.example.stardewoffline.core.datastore.AppPreferencesRepository
import com.example.stardewoffline.core.model.CataloguePage
import com.example.stardewoffline.core.model.CatalogueQuery
import com.example.stardewoffline.core.ui.component.WikiEntryGridItem
import com.example.stardewoffline.core.ui.component.WikiEntryListItem
import com.example.stardewoffline.data.Schema5ContentRepository
import com.example.stardewoffline.data.wiki.WikiCatalogue
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

data class CatalogueUiState(
    val page: CataloguePage? = null,
    val keyword: String = "",
    val selectedEntryCategory: String? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class TypeListViewModel @Inject constructor(
    saved: SavedStateHandle,
    private val catalogue: WikiCatalogue,
    private val content: Schema5ContentRepository,
    private val preferences: AppPreferencesRepository,
) : ViewModel() {
    private val categoryId = checkNotNull<String>(saved["categoryId"])
    private val mutableState = MutableStateFlow(
        CatalogueUiState(
            selectedEntryCategory = if (categoryId == "type:shop") "常用" else null,
        ),
    )
    private val mutableRoot = MutableStateFlow<File?>(null)
    val state = mutableState.asStateFlow()
    val root = mutableRoot.asStateFlow()
    private var reloadJob: Job? = null
    private var loadMoreJob: Job? = null

    init {
        viewModelScope.launch {
            preferences.preferences.map { it.activePackageId }.distinctUntilChanged().collect {
                mutableRoot.value = content.packageRoot()
                reload()
            }
        }
    }

    fun updateKeyword(value: String) {
        mutableState.value = mutableState.value.copy(keyword = value)
        reload(KEYWORD_DEBOUNCE_MS)
    }

    fun selectEntryCategory(value: String?) {
        mutableState.value = mutableState.value.copy(selectedEntryCategory = value)
        reload()
    }

    fun retry() = reload()

    fun loadMore() {
        val current = mutableState.value
        val page = current.page ?: return
        val cursor = page.nextCursor ?: return
        if (current.isLoading || loadMoreJob?.isActive == true) return
        loadMoreJob = viewModelScope.launch {
            mutableState.value = current.copy(isLoading = true, error = null)
            val result = try {
                catalogue.entries(
                    CatalogueQuery(
                        categoryId = categoryId,
                        keyword = current.keyword,
                        entryCategory = current.selectedEntryCategory,
                        cursor = cursor,
                    ),
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                val latest = mutableState.value
                if (latest.keyword == current.keyword && latest.selectedEntryCategory == current.selectedEntryCategory) {
                    mutableState.value = latest.copy(isLoading = false, error = "读取更多分类失败：${throwable.message ?: throwable::class.simpleName}")
                }
                return@launch
            }
            val latest = mutableState.value
            if (latest.keyword != current.keyword || latest.selectedEntryCategory != current.selectedEntryCategory) return@launch
            mutableState.value = when (result) {
                is AppResult.Success -> latest.copy(
                    page = page.copy(
                        entries = (page.entries + result.value.entries).distinctBy { it.id },
                        availableEntryCategories = (page.availableEntryCategories + result.value.availableEntryCategories).distinct().sorted(),
                        nextCursor = result.value.nextCursor,
                    ),
                    isLoading = false,
                    error = null,
                )
                is AppResult.Failure -> latest.copy(isLoading = false, error = result.error.message)
            }
        }
    }

    /**
     * Flow: only the latest keyword/category request can update the page.
     * Failure: query errors stay visible instead of becoming an empty Compose tree.
     */
    private fun reload(delayMillis: Long = 0) {
        reloadJob?.cancel()
        reloadJob = viewModelScope.launch {
            if (delayMillis > 0) delay(delayMillis)
            val requested = mutableState.value
            mutableState.value = requested.copy(isLoading = true, error = null)
            loadMoreJob?.cancel()
            val result = try {
                catalogue.entries(
                    CatalogueQuery(
                        categoryId = categoryId,
                        keyword = requested.keyword,
                        entryCategory = requested.selectedEntryCategory,
                    ),
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                val current = mutableState.value
                if (current.keyword == requested.keyword && current.selectedEntryCategory == requested.selectedEntryCategory) {
                    mutableState.value = current.copy(page = null, isLoading = false, error = "读取分类失败：${throwable.message ?: throwable::class.simpleName}")
                }
                return@launch
            }
            val current = mutableState.value
            if (current.keyword != requested.keyword || current.selectedEntryCategory != requested.selectedEntryCategory) return@launch
            mutableState.value = when (result) {
                is AppResult.Success -> current.copy(page = result.value, isLoading = false, error = null)
                is AppResult.Failure -> current.copy(page = null, isLoading = false, error = result.error.message)
            }
        }
    }

    private companion object {
        const val KEYWORD_DEBOUNCE_MS = 250L
    }
}

@Composable
fun TypeListRoute(
    onDetail: (String) -> Unit,
    onBack: () -> Unit = {},
    viewModel: TypeListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val root by viewModel.root.collectAsState()
    val page = state.page
    when {
        page != null -> CatalogueContent(page, state, root, onDetail, onBack, viewModel::updateKeyword, viewModel::selectEntryCategory, viewModel::loadMore)
        state.isLoading -> CatalogueLoading(onBack)
        else -> CatalogueError(state.error ?: "无法加载分类", onBack, viewModel::retry)
    }
}

private val CatalogueGreen = Color(0xFF163F37)

@Composable
private fun CatalogueContent(
    page: CataloguePage,
    state: CatalogueUiState,
    root: File?,
    onDetail: (String) -> Unit,
    onBack: () -> Unit,
    onKeywordChange: (String) -> Unit,
    onSelectEntryCategory: (String?) -> Unit,
    onLoadMore: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        Surface(color = CatalogueGreen, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(start = 12.dp, end = 20.dp, top = 12.dp, bottom = 18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack, modifier = Modifier.size(44.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = Color.White)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(page.category.title, style = MaterialTheme.typography.headlineSmall, color = Color.White)
                        Text(
                            if (page.category.entityTypes.size == 1) "${page.category.entryCount} 条本地资料" else "${page.category.entityTypes.size} 个类型 · ${page.category.entryCount} 条资料",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFC7D8D1),
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = state.keyword,
                    onValueChange = onKeywordChange,
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = TYPE_LIST_SEARCH_FIELD_DESCRIPTION },
                    placeholder = { Text("搜索本分类") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
            }
        }
        CategoryFilters(page, state.selectedEntryCategory, onSelectEntryCategory)
        if (page.entries.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("当前条件下没有匹配条目", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Column(Modifier.weight(1f).fillMaxWidth()) {
                if (page.category.entityTypes == setOf("shop") || page.category.entityTypes == setOf("quest")) {
                    // 商店/任务：无图列式，信息密度优先。
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(page.entries, key = { it.id }) { entry ->
                            WikiEntryListItem(
                                entry = entry,
                                packageRoot = root,
                                showCategoryLabel = page.category.entityTypes.size > 1,
                                onClick = { onDetail(entry.id) },
                            )
                        }
                    }
                } else {
                    val fontScale = LocalDensity.current.fontScale
                    val gridMinSize = when {
                        fontScale >= 2.0f -> 200.dp
                        fontScale >= 1.3f -> 160.dp
                        else -> 140.dp
                    }
                    LazyVerticalGrid(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        columns = GridCells.Adaptive(minSize = gridMinSize),
                        contentPadding = PaddingValues(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(page.entries, key = { it.id }) { entry ->
                            WikiEntryGridItem(
                                entry = entry,
                                packageRoot = root,
                                showCategoryLabel = page.category.entityTypes.size > 1,
                                onClick = { onDetail(entry.id) },
                            )
                        }
                    }
                }
                if (page.nextCursor != null) {
                    Button(
                        onClick = onLoadMore,
                        enabled = !state.isLoading,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    ) {
                        Text(if (state.isLoading) "正在加载…" else "加载更多资料")
                    }
                }
            }
        }
    }
}

@Composable
private fun CatalogueLoading(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        CatalogueStateHeader("正在加载分类", onBack)
        Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            Text("正在读取本地资料", modifier = Modifier.padding(top = 16.dp))
        }
    }
}

@Composable
private fun CatalogueError(message: String, onBack: () -> Unit, onRetry: () -> Unit) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        CatalogueStateHeader("无法加载分类", onBack)
        Column(Modifier.weight(1f).fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(message, color = MaterialTheme.colorScheme.error)
            Button(onClick = onRetry, modifier = Modifier.padding(top = 24.dp)) { Text("重试") }
        }
    }
}

@Composable
private fun CatalogueStateHeader(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") }
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 4.dp))
    }
}

@Composable
private fun CategoryFilters(page: CataloguePage, selected: String?, onSelect: (String?) -> Unit) {
    val categories = page.availableEntryCategories.filterNot { page.category.entityTypes.size == 1 && it == page.category.title }
    if (categories.isEmpty()) return
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        contentPadding = PaddingValues(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        val isShop = page.category.entityTypes == setOf("shop")
        if (!isShop) {
            item {
                val allLabel = if (page.category.entityTypes == setOf("villager")) "全部村民" else "全部"
                FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text(allLabel) })
            }
        }
        items(categories) { category ->
            FilterChip(selected = selected == category, onClick = { onSelect(category) }, label = { Text(category) })
        }
    }
}

private const val TYPE_LIST_SEARCH_FIELD_DESCRIPTION = "分类搜索输入框"
