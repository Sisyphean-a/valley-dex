package com.example.stardewoffline.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Chair
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Grass
import androidx.compose.material.icons.filled.Handyman
import androidx.compose.material.icons.filled.Hiking
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PestControl
import androidx.compose.material.icons.filled.PrecisionManufacturing
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SetMeal
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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

private val ValleyGreen = Color(0xFF163F37)
private val ValleyGreenSoft = Color(0xFFE1EEE7)
private val ValleyGold = Color(0xFFE1AD4B)
private val ValleyCream = Color(0xFFF5F1E7)
private val ValleyLine = Color(0xFFDED8CB)
private val ValleyMuted = Color(0xFF697873)

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
    onSearch: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val sections by viewModel.sections.collectAsState()
    HomeContent(sections, onCategory, onSearch)
}

@Composable
private fun HomeContent(
    sections: List<WikiSection>,
    onCategory: (String) -> Unit,
    onSearch: () -> Unit,
) {
    val major = sections.firstOrNull { it.id == "major" }?.categories.orEmpty()
    val catalogues = sections.filter { it.id.startsWith("catalogue-") }
    val allCategories = catalogues.flatMap(WikiSection::categories)
    val quickIds = listOf("crop", "quest", "shop", "villager")
    val quick = quickIds.mapNotNull { id -> allCategories.firstOrNull { it.id == "type:$id" } }
    val totalEntries = allCategories.sumOf(WikiCategory::entryCount)

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(ValleyCream),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        item { HomeHeader(onSearch) }
        if (quick.isNotEmpty()) {
            item { SectionHeading("快捷入口", "常用资料") }
            item { QuickAccess(quick, onCategory) }
        }
        if (major.isNotEmpty()) {
            item { SectionHeading("大类导航", "${major.size} 个主题") }
            major.chunked(2).forEach { row ->
                item(key = "major:${row.first().id}") { MajorCategoryRow(row, onCategory) }
            }
        }
        if (catalogues.isNotEmpty()) {
            item { SectionHeading("全部分类", "${allCategories.size} 类 · $totalEntries 条") }
            catalogues.forEach { section ->
                item(key = "heading:${section.id}") { CatalogueHeading(section) }
                section.categories.chunked(2).forEach { row ->
                    item(key = "row:${section.id}:${row.first().id}") { CategoryRow(row, onCategory) }
                }
            }
        }
    }
}

@Composable
private fun HomeHeader(onSearch: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().background(ValleyGreen).padding(start = 20.dp, top = 28.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(color = ValleyGold, shape = MaterialTheme.shapes.medium, modifier = Modifier.size(44.dp)) {
                Icon(Icons.Filled.Grass, contentDescription = null, tint = ValleyGreen, modifier = Modifier.padding(10.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("VALLEY INDEX", style = MaterialTheme.typography.labelMedium, color = Color(0xFFBFD6C4), fontWeight = FontWeight.Bold)
                Text("农场资料库", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(14.dp))
        Text("今天想查什么？", style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = FontWeight.ExtraBold)
        Text("人物、商店、怪物、物品与世界资料，都在这里。", style = MaterialTheme.typography.bodyMedium, color = Color(0xFFC7D8D1))
        Spacer(Modifier.height(8.dp))
        Surface(
            modifier = Modifier.fillMaxWidth().height(58.dp).clickable(role = Role.Button, onClick = onSearch),
            color = Color(0xFFFFFEFA),
            shape = MaterialTheme.shapes.large,
            shadowElevation = 3.dp,
        ) {
            Row(Modifier.padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Search, contentDescription = null, tint = ValleyMuted)
                Spacer(Modifier.width(12.dp))
                Text("搜索名称、地点或用途", color = ValleyMuted, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun SectionHeading(title: String, meta: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, top = 24.dp, end = 20.dp, bottom = 10.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
        Text(meta, style = MaterialTheme.typography.bodySmall, color = ValleyMuted)
    }
}

@Composable
private fun QuickAccess(categories: List<WikiCategory>, onCategory: (String) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
    ) {
        Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            categories.forEachIndexed { index, category ->
                if (index > 0) VerticalDivider(Modifier.height(74.dp), color = ValleyLine)
                Column(
                    modifier = Modifier.weight(1f).clickable { onCategory(category.id) }.padding(horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Icon(categoryIcon(category), contentDescription = null, tint = if (index % 2 == 0) ValleyGold else ValleyGreen, modifier = Modifier.size(28.dp))
                    Text(category.title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text("${category.entryCount} 条", style = MaterialTheme.typography.labelSmall, color = ValleyMuted)
                }
            }
        }
    }
}

@Composable
private fun MajorCategoryRow(categories: List<WikiCategory>, onCategory: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        categories.forEach { category -> MajorCategoryCard(category, Modifier.weight(1f), onCategory) }
        if (categories.size == 1) Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun MajorCategoryCard(category: WikiCategory, modifier: Modifier, onCategory: (String) -> Unit) {
    Surface(
        modifier = modifier
            .testTag("home-category:${category.id}")
            .semantics { contentDescription = "打开分类 ${category.title}" }
            .clickable { onCategory(category.id) },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(color = majorCategoryColor(category.id), shape = MaterialTheme.shapes.medium, modifier = Modifier.size(44.dp)) {
                Icon(categoryIcon(category), contentDescription = null, tint = ValleyGreen, modifier = Modifier.padding(10.dp))
            }
            Text(category.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            Text("${category.entityTypes.size} 类 · ${category.entryCount} 条", style = MaterialTheme.typography.bodySmall, color = ValleyMuted)
        }
    }
}

@Composable
private fun CatalogueHeading(section: WikiSection) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, top = 18.dp, end = 20.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(section.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = ValleyGreen)
        Text("${section.categories.size} 类", style = MaterialTheme.typography.labelMedium, color = ValleyMuted)
    }
}

@Composable
private fun CategoryRow(categories: List<WikiCategory>, onCategory: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        categories.forEach { category -> CategoryCard(category, Modifier.weight(1f), onCategory) }
        if (categories.size == 1) Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun CategoryCard(category: WikiCategory, modifier: Modifier, onCategory: (String) -> Unit) {
    Surface(
        modifier = modifier
            .height(92.dp)
            .testTag("home-category:${category.id}")
            .semantics { contentDescription = "打开分类 ${category.title}" }
            .clickable { onCategory(category.id) },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = CardDefaults.outlinedCardBorder(),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(46.dp).clip(MaterialTheme.shapes.medium).background(categoryColor(category)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(categoryIcon(category), contentDescription = null, tint = ValleyGreen, modifier = Modifier.size(25.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(category.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${category.entryCount} 个条目", style = MaterialTheme.typography.bodySmall, color = ValleyMuted)
            }
            Text("›", style = MaterialTheme.typography.titleLarge, color = Color(0xFFA4ACA8))
        }
    }
}

private fun categoryIcon(category: WikiCategory): ImageVector {
    val type = category.id.removePrefix("type:")
    return when (type) {
        "farm" -> Icons.Filled.Grass
        "community" -> Icons.Filled.People
        "exploration" -> Icons.Filled.Public
        "missions" -> Icons.Filled.Assignment
        "crafting" -> Icons.Filled.Handyman
        "object" -> Icons.Filled.Inventory2
        "crop" -> Icons.Filled.Grass
        "big_craftable" -> Icons.Filled.PrecisionManufacturing
        "tool" -> Icons.Filled.Build
        "furniture" -> Icons.Filled.Chair
        "villager" -> Icons.Filled.People
        "shop" -> Icons.Filled.Storefront
        "monster" -> Icons.Filled.PestControl
        "fish" -> Icons.Filled.SetMeal
        "mineral" -> Icons.Filled.Diamond
        "drop" -> Icons.Filled.AutoAwesome
        "weapon" -> Icons.Filled.Security
        "footwear" -> Icons.Filled.Hiking
        "ring" -> Icons.Filled.RadioButtonChecked
        "trinket" -> Icons.Filled.Stars
        "ginger_island" -> Icons.Filled.Public
        "achievement" -> Icons.Filled.EmojiEvents
        "bundle" -> Icons.Filled.Inventory2
        "quest" -> Icons.Filled.Assignment
        "special_order" -> Icons.Filled.TaskAlt
        "cooking_recipe" -> Icons.Filled.Restaurant
        "crafting_recipe" -> Icons.Filled.Handyman
        "tailoring_recipe" -> Icons.Filled.Checkroom
        else -> Icons.Filled.Category
    }
}

private fun majorCategoryColor(id: String) = when (id) {
    "farm" -> Color(0xFFDDEAD9)
    "community" -> Color(0xFFF2E3CD)
    "exploration" -> Color(0xFFDDE8E4)
    "missions" -> Color(0xFFF3E9C9)
    else -> Color(0xFFECE2DA)
}

private fun categoryColor(category: WikiCategory): Color = when (category.id.removePrefix("type:")) {
    "crop", "villager", "fish", "tool", "crafting_recipe" -> ValleyGreenSoft
    "shop", "quest", "achievement", "ginger_island", "big_craftable" -> Color(0xFFF4E8C9)
    "monster", "weapon", "drop", "special_order" -> Color(0xFFF2E1D8)
    else -> Color(0xFFECE8DF)
}
