package com.example.stardewoffline.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.stardewoffline.core.common.AppResult
import com.example.stardewoffline.core.datastore.AppPreferencesRepository
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
    private val mutableRoot = MutableStateFlow<File?>(null)
    val query = mutableQuery.asStateFlow()
    val results = mutableResults.asStateFlow()
    val error = mutableError.asStateFlow()
    val root = mutableRoot.asStateFlow()
    private var searchJob: Job? = null

    init {
        val activePackageIds = preferences.preferences.map { it.activePackageId }.distinctUntilChanged()
        viewModelScope.launch {
            activePackageIds.collectLatest {
                mutableRoot.value = content.packageRoot()
                mutableResults.value = emptyList()
                mutableError.value = null
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

    private fun search(delayMillis: Long = 0) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            if (delayMillis > 0) delay(delayMillis)
            val requestedQuery = mutableQuery.value
            when (val response = catalogue.search(com.example.stardewoffline.core.model.WikiSearchQuery(requestedQuery))) {
                is AppResult.Success -> {
                    if (mutableQuery.value == requestedQuery) {
                        mutableResults.value = response.value
                        mutableError.value = null
                    }
                }
                is AppResult.Failure -> {
                    if (mutableQuery.value == requestedQuery) {
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
    val root by viewModel.root.collectAsState()
    SearchScreen(
        query = query,
        results = results,
        error = error,
        root = root,
        onQuery = viewModel::updateQuery,
        onSubmit = viewModel::submitSearch,
        onDetail = onDetail,
    )
}

@Composable
private fun SearchScreen(
    query: String,
    results: List<com.example.stardewoffline.core.model.WikiSearchHit>,
    error: String?,
    root: File?,
    onQuery: (String) -> Unit,
    onSubmit: () -> Unit,
    onDetail: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(androidx.compose.material3.MaterialTheme.colorScheme.background),
        verticalArrangement = Arrangement.spacedBy(7.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item {
            Surface(color = Color(0xFF163F37), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("搜索图鉴", style = androidx.compose.material3.MaterialTheme.typography.headlineSmall, color = Color.White)
                    Text("中文、英文、拼音和别名均可搜索", style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = Color(0xFFC7D8D1))
                    OutlinedTextField(
                        value = query,
                        onValueChange = onQuery,
                        modifier = Modifier.fillMaxWidth().semantics { contentDescription = SEARCH_FIELD_DESCRIPTION },
                        label = { Text("搜索名称、地点或用途") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface,
                        ),
                    )
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (query.isBlank()) "输入关键词开始探索" else "找到 ${results.size} 条结果", style = androidx.compose.material3.MaterialTheme.typography.labelLarge, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                if (query.isNotBlank()) Text("离线检索", style = androidx.compose.material3.MaterialTheme.typography.labelSmall, color = Color(0xFF163F37))
            }
        }
        error?.let { message -> item { Text(message, modifier = Modifier.padding(horizontal = 20.dp), color = Color(0xFFB3261E)) } }
        if (query.isNotBlank() && results.isEmpty() && error == null) {
            item { Text("没有找到匹配条目", modifier = Modifier.padding(horizontal = 20.dp)) }
        }
        items(results, key = { it.entry.id }) { hit ->
            WikiEntryListItem(entry = hit.entry, packageRoot = root, modifier = Modifier.padding(horizontal = 16.dp), onClick = { onDetail(hit.entry.id) })
        }
    }
}

private const val SEARCH_FIELD_DESCRIPTION = "图鉴搜索输入框"
