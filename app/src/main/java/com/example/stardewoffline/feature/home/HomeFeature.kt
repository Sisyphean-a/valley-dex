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
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.stardewoffline.core.common.getOrNull
import com.example.stardewoffline.core.datastore.AppPreferencesRepository
import com.example.stardewoffline.core.model.WikiCategory
import com.example.stardewoffline.core.model.WikiSection
import com.example.stardewoffline.data.wiki.WikiCatalogue
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val catalogue: WikiCatalogue,
    private val preferences: AppPreferencesRepository,
) : ViewModel() {
    private val mutableSections = MutableStateFlow<List<WikiSection>>(emptyList())
    val sections = mutableSections.asStateFlow()

    init {
        viewModelScope.launch {
            preferences.preferences.map { it.activePackageId }.distinctUntilChanged().collect {
                mutableSections.value = catalogue.sections().getOrNull().orEmpty()
            }
        }
    }
}

@Composable
fun HomeRoute(
    onCategory: (String) -> Unit,
    onDetail: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val sections by viewModel.sections.collectAsState()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("星露谷离线图鉴", style = MaterialTheme.typography.headlineMedium) }
        item { Text("离线探索你的农场生活", style = MaterialTheme.typography.bodyLarge) }
        sections.forEach { section ->
            item { SectionTitle(section.title) }
            items(section.categories, key = WikiCategory::id) { category ->
                CategoryCard(category, onCategory)
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun CategoryCard(category: WikiCategory, onCategory: (String) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home-category:${category.id}")
            .clickable { onCategory(category.id) },
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(category.title, style = MaterialTheme.typography.titleMedium)
            Text("${category.entryCount} 个条目", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
