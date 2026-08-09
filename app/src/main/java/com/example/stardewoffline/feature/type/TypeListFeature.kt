package com.example.stardewoffline.feature.type

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.stardewoffline.core.common.AppResult
import com.example.stardewoffline.core.datastore.AppPreferencesRepository
import com.example.stardewoffline.core.model.CatalogueDisplayMode
import com.example.stardewoffline.core.model.CataloguePage
import com.example.stardewoffline.core.model.CatalogueQuery
import com.example.stardewoffline.core.ui.component.WikiEntryGridItem
import com.example.stardewoffline.core.ui.component.WikiEntryListItem
import com.example.stardewoffline.data.ContentRepository
import com.example.stardewoffline.data.wiki.WikiCatalogue
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

data class CatalogueUiState(
    val page: CataloguePage? = null,
    val keyword: String = "",
    val selectedEntryCategory: String? = null,
    val displayMode: CatalogueDisplayMode = CatalogueDisplayMode.List,
    val isLoading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class TypeListViewModel @Inject constructor(
    saved: SavedStateHandle,
    private val catalogue: WikiCatalogue,
    private val content: ContentRepository,
    private val preferences: AppPreferencesRepository,
) : ViewModel() {
    private val categoryId = checkNotNull<String>(saved["categoryId"])
    private val mutableState = MutableStateFlow(CatalogueUiState())
    private val mutableRoot = MutableStateFlow<File?>(null)
    val state = mutableState.asStateFlow()
    val root = mutableRoot.asStateFlow()
    private var reloadJob: Job? = null

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

    fun setDisplayMode(value: CatalogueDisplayMode) {
        mutableState.value = mutableState.value.copy(displayMode = value)
    }

    fun retry() = reload()

    /**
     * Flow: only the latest keyword/category request can update the page.
     * Failure: query errors stay visible instead of being converted into an empty Compose tree.
     */
    private fun reload(delayMillis: Long = 0) {
        reloadJob?.cancel()
        reloadJob = viewModelScope.launch {
            if (delayMillis > 0) delay(delayMillis)
            val requested = mutableState.value
            mutableState.value = requested.copy(isLoading = true, error = null)
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
fun TypeListRoute(onDetail: (String) -> Unit, viewModel: TypeListViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val root by viewModel.root.collectAsState()
    val page = state.page
    when {
        page != null -> CatalogueContent(page, state, root, onDetail, viewModel::updateKeyword, viewModel::selectEntryCategory, viewModel::setDisplayMode)
        state.isLoading -> CatalogueLoading()
        else -> CatalogueError(state.error ?: "无法加载分类", viewModel::retry)
    }
}

@Composable
private fun CatalogueContent(
    page: CataloguePage,
    state: CatalogueUiState,
    root: File?,
    onDetail: (String) -> Unit,
    onKeywordChange: (String) -> Unit,
    onSelectEntryCategory: (String?) -> Unit,
    onDisplayMode: (CatalogueDisplayMode) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            page.category.title,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        OutlinedTextField(
            value = state.keyword,
            onValueChange = onKeywordChange,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).semantics { contentDescription = TYPE_LIST_SEARCH_FIELD_DESCRIPTION },
            label = { Text("在此分类中搜索") },
            singleLine = true,
        )
        CategoryFilters(page, state.selectedEntryCategory, onSelectEntryCategory)
        DisplayModeSwitch(state.displayMode, onDisplayMode)
        if (state.displayMode == CatalogueDisplayMode.List) {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(page.entries, key = { it.id }) { entry ->
                    WikiEntryListItem(entry, root, onClick = { onDetail(entry.id) })
                }
            }
        } else {
            LazyVerticalGrid(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(page.entries, key = { it.id }) { entry ->
                    WikiEntryGridItem(entry, root, onClick = { onDetail(entry.id) })
                }
            }
        }
    }
}

@Composable
private fun CatalogueLoading() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Text("正在加载分类", modifier = Modifier.padding(top = 16.dp))
    }
}

@Composable
private fun CatalogueError(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("无法加载分类", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.error)
        Text(message, modifier = Modifier.padding(top = 12.dp))
        Button(onClick = onRetry, modifier = Modifier.padding(top = 24.dp)) { Text("重试") }
    }
}

@Composable
private fun CategoryFilters(page: CataloguePage, selected: String?, onSelect: (String?) -> Unit) {
    if (page.availableEntryCategories.isEmpty()) return
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        contentPadding = PaddingValues(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text("全部") }) }
        items(page.availableEntryCategories) { category ->
            FilterChip(selected = selected == category, onClick = { onSelect(category) }, label = { Text(category) })
        }
    }
}

private const val TYPE_LIST_SEARCH_FIELD_DESCRIPTION = "分类搜索输入框"

@Composable
private fun DisplayModeSwitch(mode: CatalogueDisplayMode, onSelect: (CatalogueDisplayMode) -> Unit) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        contentPadding = PaddingValues(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { FilterChip(selected = mode == CatalogueDisplayMode.List, onClick = { onSelect(CatalogueDisplayMode.List) }, label = { Text("列表") }) }
        item { FilterChip(selected = mode == CatalogueDisplayMode.Grid, onClick = { onSelect(CatalogueDisplayMode.Grid) }, label = { Text("网格") }) }
    }
}
