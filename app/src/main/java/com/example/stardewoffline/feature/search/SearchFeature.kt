package com.example.stardewoffline.feature.search

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.stardewoffline.core.common.AppResult
import com.example.stardewoffline.core.model.SearchResult
import com.example.stardewoffline.core.ui.component.EntityListItem
import com.example.stardewoffline.data.ContentRepository
import com.example.stardewoffline.data.SearchRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class SearchViewModel @Inject constructor(private val search: SearchRepository, private val content: ContentRepository) : ViewModel() {
    private val mutableQuery = MutableStateFlow(""); private val mutableResults = MutableStateFlow<List<SearchResult>>(emptyList()); private val mutableRoot = MutableStateFlow<File?>(null)
    val query = mutableQuery.asStateFlow(); val results = mutableResults.asStateFlow(); val root = mutableRoot.asStateFlow(); private var searchJob: Job? = null
    init { viewModelScope.launch { mutableRoot.value = content.packageRoot() } }
    fun updateQuery(value: String) { mutableQuery.value = value; searchJob?.cancel(); searchJob = viewModelScope.launch { delay(150); mutableResults.value = (search.search(value) as? AppResult.Success)?.value.orEmpty() } }
}

@Composable
fun SearchRoute(onDetail: (String) -> Unit, viewModel: SearchViewModel = hiltViewModel()) {
    val query by viewModel.query.collectAsState(); val results by viewModel.results.collectAsState(); val root by viewModel.root.collectAsState()
    LazyColumn(Modifier.fillMaxSize()) {
        item { OutlinedTextField(value = query, onValueChange = viewModel::updateQuery, modifier = Modifier.padding(16.dp), label = { androidx.compose.material3.Text("搜索中文、英文、拼音或别名") }, singleLine = true) }
        items(results, key = { it.summary.id }) { result -> EntityListItem(result.summary, root, result.reason, onClick = { onDetail(result.summary.id) }) }
    }
}
