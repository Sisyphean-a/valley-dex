package com.example.stardewoffline.feature.type

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.stardewoffline.core.common.getOrNull
import com.example.stardewoffline.core.model.CatalogueDisplayMode
import com.example.stardewoffline.core.model.CataloguePage
import com.example.stardewoffline.core.model.CatalogueQuery
import com.example.stardewoffline.core.ui.component.WikiEntryListItem
import com.example.stardewoffline.data.ContentRepository
import com.example.stardewoffline.data.wiki.WikiCatalogue
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CatalogueUiState(
    val page: CataloguePage? = null,
    val keyword: String = "",
    val selectedEntryCategory: String? = null,
    val displayMode: CatalogueDisplayMode = CatalogueDisplayMode.List,
)

@HiltViewModel
class TypeListViewModel @Inject constructor(
    saved: SavedStateHandle,
    private val catalogue: WikiCatalogue,
    private val content: ContentRepository,
) : ViewModel() {
    private val categoryId = checkNotNull<String>(saved["categoryId"])
    private val mutableState = MutableStateFlow(CatalogueUiState())
    private val mutableRoot = MutableStateFlow<File?>(null)
    val state = mutableState.asStateFlow()
    val root = mutableRoot.asStateFlow()

    init {
        viewModelScope.launch {
            mutableRoot.value = content.packageRoot()
            reload()
        }
    }

    fun updateKeyword(value: String) {
        mutableState.value = mutableState.value.copy(keyword = value)
        reload()
    }

    fun selectEntryCategory(value: String?) {
        mutableState.value = mutableState.value.copy(selectedEntryCategory = value)
        reload()
    }

    fun setDisplayMode(value: CatalogueDisplayMode) {
        mutableState.value = mutableState.value.copy(displayMode = value)
        reload()
    }

    private fun reload() = viewModelScope.launch {
        val current = mutableState.value
        val page = catalogue.entries(
            CatalogueQuery(
                categoryId = categoryId,
                keyword = current.keyword,
                entryCategory = current.selectedEntryCategory,
                displayMode = current.displayMode,
            ),
        ).getOrNull()
        mutableState.value = current.copy(page = page)
    }
}

@Composable
fun TypeListRoute(onDetail: (String) -> Unit, viewModel: TypeListViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val root by viewModel.root.collectAsState()
    val page = state.page ?: return
    Column(Modifier.fillMaxSize()) {
        Text(page.category.title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(20.dp))
        OutlinedTextField(
            value = state.keyword,
            onValueChange = viewModel::updateKeyword,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            label = { Text("在此分类中搜索") },
            singleLine = true,
        )
        CategoryFilters(page, state.selectedEntryCategory, viewModel::selectEntryCategory)
        DisplayModeSwitch(state.displayMode, viewModel::setDisplayMode)
        if (state.displayMode == CatalogueDisplayMode.List) {
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(page.entries, key = { it.id }) { entry -> WikiEntryListItem(entry, root, onClick = { onDetail(entry.id) }) }
            }
        } else {
            LazyVerticalGrid(modifier = Modifier.weight(1f), columns = GridCells.Fixed(2), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(page.entries, key = { it.id }) { entry ->
                    OutlinedButton(onClick = { onDetail(entry.id) }, modifier = Modifier.fillMaxWidth()) { Text(entry.title) }
                }
            }
        }
    }
}

@Composable
private fun CategoryFilters(page: CataloguePage, selected: String?, onSelect: (String?) -> Unit) {
    if (page.availableEntryCategories.isEmpty()) return
    LazyRow(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item { FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text("全部") }) }
        items(page.availableEntryCategories) { category ->
            FilterChip(selected = selected == category, onClick = { onSelect(category) }, label = { Text(category) })
        }
    }
}

@Composable
private fun DisplayModeSwitch(mode: CatalogueDisplayMode, onSelect: (CatalogueDisplayMode) -> Unit) {
    LazyRow(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item { FilterChip(selected = mode == CatalogueDisplayMode.List, onClick = { onSelect(CatalogueDisplayMode.List) }, label = { Text("列表") }) }
        item { FilterChip(selected = mode == CatalogueDisplayMode.Grid, onClick = { onSelect(CatalogueDisplayMode.Grid) }, label = { Text("网格") }) }
    }
}
