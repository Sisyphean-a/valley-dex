package com.example.stardewoffline.feature.type

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.stardewoffline.core.common.AppResult
import com.example.stardewoffline.core.model.EntitySummary
import com.example.stardewoffline.core.ui.component.EntityListItem
import com.example.stardewoffline.data.ContentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class TypeListViewModel @Inject constructor(saved: SavedStateHandle, private val content: ContentRepository) : ViewModel() {
    val type: String = checkNotNull(saved["type"])
    private val mutableItems = MutableStateFlow<List<EntitySummary>>(emptyList())
    private val mutableRoot = MutableStateFlow<File?>(null)
    val items = mutableItems.asStateFlow(); val root = mutableRoot.asStateFlow()
    init { viewModelScope.launch { mutableRoot.value = content.packageRoot(); (content.summaries(type) as? AppResult.Success)?.let { mutableItems.value = it.value } } }
}

@Composable
fun TypeListRoute(onDetail: (String) -> Unit, viewModel: TypeListViewModel = hiltViewModel()) {
    val items by viewModel.items.collectAsState(); val root by viewModel.root.collectAsState()
    LazyColumn(Modifier.fillMaxSize()) { items(items, key = { it.id }) { EntityListItem(it, root, onClick = { onDetail(it.id) }) } }
}
