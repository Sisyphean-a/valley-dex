package com.example.stardewoffline.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.stardewoffline.core.common.getOrNull
import com.example.stardewoffline.core.model.EntitySummary
import com.example.stardewoffline.core.model.EntityTypeCount
import com.example.stardewoffline.core.ui.component.EntityListItem
import com.example.stardewoffline.data.ContentRepository
import com.example.stardewoffline.data.UserDataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val content: ContentRepository,
    private val user: UserDataRepository,
) : ViewModel() {
    private val mutableTypes = MutableStateFlow<List<EntityTypeCount>>(emptyList())
    private val mutableRecent = MutableStateFlow<List<EntitySummary>>(emptyList())
    private val mutableRoot = MutableStateFlow<File?>(null)
    val types = mutableTypes.asStateFlow()
    val recent = mutableRecent.asStateFlow()
    val root = mutableRoot.asStateFlow()

    init {
        viewModelScope.launch {
            mutableRoot.value = content.packageRoot()
            mutableTypes.value = content.typeCounts().getOrNull().orEmpty()
            user.history().collect { history ->
                val records = history.take(5)
                val summaries = content.summaries(records.map { it.entityId }).getOrNull().orEmpty()
                mutableRecent.value = records.mapNotNull { summaries[it.entityId] }
            }
        }
    }
}

@Composable
fun HomeRoute(onType: (String) -> Unit, onDetail: (String) -> Unit, viewModel: HomeViewModel = hiltViewModel()) {
    val types by viewModel.types.collectAsState()
    val recent by viewModel.recent.collectAsState()
    val root by viewModel.root.collectAsState()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("星露谷离线图鉴", style = MaterialTheme.typography.headlineSmall) }
        if (recent.isNotEmpty()) {
            item { Text("最近浏览", style = MaterialTheme.typography.titleMedium) }
            items(recent, key = EntitySummary::id) { EntityListItem(it, root) { onDetail(it.id) } }
        }
        item { Text("全部分类", style = MaterialTheme.typography.titleMedium) }
        items(types.filter { it.count > 0 }, key = EntityTypeCount::type) { type ->
            Column(Modifier.fillMaxWidth().clickable { onType(type.type) }.padding(vertical = 12.dp)) {
                Text(type.type, style = MaterialTheme.typography.titleMedium)
                Text("${type.count} 个条目", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
