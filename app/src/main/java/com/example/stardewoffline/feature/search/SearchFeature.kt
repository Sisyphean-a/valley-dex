package com.example.stardewoffline.feature.search

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.stardewoffline.core.common.AppResult
import com.example.stardewoffline.core.common.getOrNull
import com.example.stardewoffline.core.database.user.RecentSearchEntity
import com.example.stardewoffline.core.datastore.AppPreferencesRepository
import com.example.stardewoffline.core.model.WikiEntry
import com.example.stardewoffline.core.model.WikiEntrySummary
import com.example.stardewoffline.core.ui.component.WikiEntryListItem
import com.example.stardewoffline.data.ContentRepository
import com.example.stardewoffline.data.SearchQueryNormalizer
import com.example.stardewoffline.data.UserDataRepository
import com.example.stardewoffline.data.wiki.WikiCatalogue
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val catalogue: WikiCatalogue,
    private val content: ContentRepository,
    private val user: UserDataRepository,
    private val preferences: AppPreferencesRepository,
) : ViewModel() {
    private val mutableQuery = MutableStateFlow("")
    private val mutableResults = MutableStateFlow<List<com.example.stardewoffline.core.model.WikiSearchHit>>(emptyList())
    private val mutableError = MutableStateFlow<String?>(null)
    private val mutableSelectedTypes = MutableStateFlow<Set<String>>(emptySet())
    private val mutableFacets = MutableStateFlow<Map<String, String>>(emptyMap())
    private val mutableRecentViewed = MutableStateFlow<List<WikiEntrySummary>>(emptyList())
    private val mutableRoot = MutableStateFlow<File?>(null)
    val query = mutableQuery.asStateFlow()
    val results = mutableResults.asStateFlow()
    val error = mutableError.asStateFlow()
    val selectedTypes = mutableSelectedTypes.asStateFlow()
    val facets = mutableFacets.asStateFlow()
    val recentViewed = mutableRecentViewed.asStateFlow()
    val root = mutableRoot.asStateFlow()
    val recent = user.recentSearches()
    private var searchJob: Job? = null

    init {
        val activePackageIds = preferences.preferences.map { it.activePackageId }.distinctUntilChanged()
        viewModelScope.launch {
            combine(user.history(), activePackageIds) { history, _ -> history }.collectLatest { history ->
                mutableRoot.value = content.packageRoot()
                val summaries = catalogue.summaries(history.take(5).map { it.entityId }).getOrNull().orEmpty()
                mutableRecentViewed.value = history.take(5).mapNotNull { summaries[it.entityId] }
            }
        }
        viewModelScope.launch {
            activePackageIds.collect {
                mutableRoot.value = content.packageRoot()
                mutableSelectedTypes.value = emptySet()
                mutableResults.value = emptyList()
                mutableError.value = null
                mutableFacets.value = catalogue.sections().getOrNull()
                    ?.firstOrNull { section -> section.id == "all" }
                    ?.categories
                    ?.associate { category -> category.entityTypes.single() to category.title }
                    .orEmpty()
                if (mutableQuery.value.isNotBlank()) search()
            }
        }
    }

    fun updateQuery(value: String) {
        mutableQuery.value = value
        search(DEBOUNCE_MS)
    }

    fun submitSearch() = viewModelScope.launch {
        val query = SearchQueryNormalizer.normalize(mutableQuery.value) ?: return@launch
        if (preferences.current().searchHistoryEnabled) user.rememberSearch(query.normalized, mutableQuery.value.trim())
    }

    fun selectRecent(value: String) { updateQuery(value); submitSearch() }

    fun toggleType(value: String) {
        mutableSelectedTypes.value = mutableSelectedTypes.value.toMutableSet().apply { if (!add(value)) remove(value) }
        search()
    }

    private fun search(delayMillis: Long = 0) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            if (delayMillis > 0) delay(delayMillis)
            val requestedQuery = mutableQuery.value
            val requestedTypes = mutableSelectedTypes.value
            when (val response = catalogue.search(com.example.stardewoffline.core.model.WikiSearchQuery(requestedQuery, requestedTypes))) {
                is AppResult.Success -> {
                    if (mutableQuery.value == requestedQuery && mutableSelectedTypes.value == requestedTypes) {
                        mutableResults.value = response.value
                        mutableError.value = null
                    }
                }
                is AppResult.Failure -> {
                    if (mutableQuery.value == requestedQuery && mutableSelectedTypes.value == requestedTypes) {
                        mutableResults.value = emptyList()
                        mutableError.value = response.error.message
                    }
                }
            }
        }
    }

    private companion object { const val DEBOUNCE_MS = 250L }
}

@Composable
fun SearchRoute(onDetail: (String) -> Unit, viewModel: SearchViewModel = hiltViewModel()) {
    val query by viewModel.query.collectAsState()
    val results by viewModel.results.collectAsState()
    val error by viewModel.error.collectAsState()
    val selectedTypes by viewModel.selectedTypes.collectAsState()
    val facets by viewModel.facets.collectAsState()
    val recent by viewModel.recent.collectAsState(emptyList())
    val recentViewed by viewModel.recentViewed.collectAsState()
    val root by viewModel.root.collectAsState()
    SearchScreen(
        query = query,
        results = results,
        error = error,
        recent = recent,
        recentViewed = recentViewed,
        selectedTypes = selectedTypes,
        facets = facets,
        root = root,
        onQuery = viewModel::updateQuery,
        onSubmit = viewModel::submitSearch,
        onRecent = viewModel::selectRecent,
        onType = viewModel::toggleType,
        onDetail = onDetail,
    )
}

@Composable
private fun SearchScreen(
    query: String,
    results: List<com.example.stardewoffline.core.model.WikiSearchHit>,
    error: String?,
    recent: List<RecentSearchEntity>,
    recentViewed: List<WikiEntrySummary>,
    selectedTypes: Set<String>,
    facets: Map<String, String>,
    root: File?,
    onQuery: (String) -> Unit,
    onSubmit: () -> Unit,
    onRecent: (String) -> Unit,
    onType: (String) -> Unit,
    onDetail: (String) -> Unit,
) {
    val visible = results
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            OutlinedTextField(
                value = query,
                onValueChange = onQuery,
                modifier = Modifier.padding(16.dp).semantics { contentDescription = SEARCH_FIELD_DESCRIPTION },
                label = { Text("搜索中文、英文、拼音或别名") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
            )
        }
        item { facets.forEach { (id, label) -> FilterChip(selected = id in selectedTypes, onClick = { onType(id) }, label = { Text(label) }) } }
        error?.let { message -> item { Text(message, modifier = Modifier.padding(horizontal = 16.dp)) } }
        if (query.isBlank() && recentViewed.isNotEmpty()) {
            item { SearchSectionTitle("最近浏览") }
            items(recentViewed, key = WikiEntrySummary::id) { entry ->
                WikiEntryListItem(entry, root, onClick = { onDetail(entry.id) })
            }
        }
        if (query.isBlank() && recent.isNotEmpty()) {
            item { SearchSectionTitle("最近搜索") }
            items(recent, key = { it.normalizedQuery }) { SearchHistoryItem(it, onRecent) }
        }
        if (query.isNotBlank() && visible.isEmpty() && error == null) item { Text("没有找到匹配条目", modifier = Modifier.padding(16.dp)) }
        items(visible, key = { it.entry.id }) { hit -> WikiEntryListItem(hit.entry, root, onClick = { onDetail(hit.entry.id) }) }
    }
}

@Composable
private fun SearchSectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun SearchHistoryItem(item: RecentSearchEntity, onClick: (String) -> Unit) {
    FilterChip(selected = false, onClick = { onClick(item.displayQuery) }, label = { Text(item.displayQuery) }, modifier = Modifier.padding(horizontal = 16.dp))
}

private const val SEARCH_FIELD_DESCRIPTION = "图鉴搜索输入框"
