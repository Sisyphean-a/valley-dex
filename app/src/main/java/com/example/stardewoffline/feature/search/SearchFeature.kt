package com.example.stardewoffline.feature.search

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.ImeAction
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.stardewoffline.core.common.AppResult
import com.example.stardewoffline.core.database.user.RecentSearchEntity
import com.example.stardewoffline.core.model.SearchResult
import com.example.stardewoffline.core.ui.component.EntityListItem
import com.example.stardewoffline.data.ContentRepository
import com.example.stardewoffline.data.SearchQueryNormalizer
import com.example.stardewoffline.data.SearchRepository
import com.example.stardewoffline.data.UserDataRepository
import com.example.stardewoffline.core.datastore.AppPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val search: SearchRepository,
    private val content: ContentRepository,
    private val user: UserDataRepository,
    private val preferences: AppPreferencesRepository,
) : ViewModel() {
    private val mutableQuery = MutableStateFlow("")
    private val mutableResults = MutableStateFlow<List<SearchResult>>(emptyList())
    private val mutableError = MutableStateFlow<String?>(null)
    private val mutableSelectedTypes = MutableStateFlow<Set<String>>(emptySet())
    private val mutableRoot = MutableStateFlow<File?>(null)
    val query = mutableQuery.asStateFlow()
    val results = mutableResults.asStateFlow()
    val error = mutableError.asStateFlow()
    val selectedTypes = mutableSelectedTypes.asStateFlow()
    val root = mutableRoot.asStateFlow()
    val recent = user.recentSearches()
    private var searchJob: Job? = null

    init { viewModelScope.launch { mutableRoot.value = content.packageRoot() } }

    fun updateQuery(value: String) {
        mutableQuery.value = value
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(150)
            when (val response = search.search(value)) {
                is AppResult.Success -> {
                    mutableResults.value = response.value
                    mutableError.value = null
                }
                is AppResult.Failure -> {
                    mutableResults.value = emptyList()
                    mutableError.value = response.error.message
                }
            }
        }
    }

    fun submitSearch() = viewModelScope.launch {
        val query = SearchQueryNormalizer.normalize(mutableQuery.value) ?: return@launch
        if (preferences.current().searchHistoryEnabled) user.rememberSearch(query.normalized, mutableQuery.value.trim())
    }

    fun selectRecent(value: String) { updateQuery(value); submitSearch() }

    fun toggleType(value: String) {
        mutableSelectedTypes.value = mutableSelectedTypes.value.toMutableSet().apply { if (!add(value)) remove(value) }
    }
}

@Composable
fun SearchRoute(onDetail: (String) -> Unit, viewModel: SearchViewModel = hiltViewModel()) {
    val query by viewModel.query.collectAsState()
    val results by viewModel.results.collectAsState()
    val error by viewModel.error.collectAsState()
    val selectedTypes by viewModel.selectedTypes.collectAsState()
    val recent by viewModel.recent.collectAsState(emptyList())
    val root by viewModel.root.collectAsState()
    SearchScreen(query, results, error, recent, selectedTypes, root, viewModel::updateQuery, viewModel::submitSearch, viewModel::selectRecent, viewModel::toggleType, onDetail)
}

@Composable
private fun SearchScreen(
    query: String,
    results: List<SearchResult>,
    error: String?,
    recent: List<RecentSearchEntity>,
    selectedTypes: Set<String>,
    root: File?,
    onQuery: (String) -> Unit,
    onSubmit: () -> Unit,
    onRecent: (String) -> Unit,
    onType: (String) -> Unit,
    onDetail: (String) -> Unit,
) {
    val types = results.map { it.summary.entityType }.distinct()
    val visible = results.filter { selectedTypes.isEmpty() || it.summary.entityType in selectedTypes }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            OutlinedTextField(
                value = query,
                onValueChange = onQuery,
                modifier = Modifier.padding(16.dp),
                label = { Text("搜索中文、英文、拼音或别名") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
            )
        }
        item { types.forEach { FilterChip(selected = it in selectedTypes, onClick = { onType(it) }, label = { Text(it) }) } }
        error?.let { message -> item { Text(message, modifier = Modifier.padding(horizontal = 16.dp)) } }
        if (query.isBlank()) items(recent, key = { it.normalizedQuery }) { SearchHistoryItem(it, onRecent) }
        items(visible, key = { it.summary.id }) { result -> EntityListItem(result.summary, root, result.reason) { onDetail(result.summary.id) } }
    }
}

@Composable
private fun SearchHistoryItem(item: RecentSearchEntity, onClick: (String) -> Unit) {
    FilterChip(selected = false, onClick = { onClick(item.displayQuery) }, label = { Text(item.displayQuery) }, modifier = Modifier.padding(horizontal = 16.dp))
}
