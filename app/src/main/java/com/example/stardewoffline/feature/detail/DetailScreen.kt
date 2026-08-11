package com.example.stardewoffline.feature.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.stardewoffline.core.model.EntryFact
import com.example.stardewoffline.core.model.EntryImage
import com.example.stardewoffline.core.model.EntryRelation
import com.example.stardewoffline.core.model.EntrySection
import com.example.stardewoffline.core.model.RelationTarget
import com.example.stardewoffline.core.model.WikiEntry
import com.example.stardewoffline.core.model.WikiEntrySubmenu
import com.example.stardewoffline.core.model.WikiEntrySubmenuItem
import com.example.stardewoffline.core.ui.component.EntityImage

private val DetailGreen = Color(0xFF163F37)
private val DetailGold = Color(0xFFE1AD4B)

@Composable
fun DetailScreen(
    state: DetailUiState,
    favorite: Boolean,
    onBack: () -> Unit,
    onFavorite: () -> Unit,
    onDetail: (String) -> Unit,
) {
    val entry = state.entry
    if (entry == null) {
        DetailLoading(state.error, onBack)
        return
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { DetailTopBar(entry.title, favorite, onBack, onFavorite) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { DetailHeader(entry, state.packageRoot, Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
            items(entry.sections, key = EntrySection::title) { section -> EntrySectionCard(section, Modifier.padding(horizontal = 16.dp)) }
            items(entry.submenus, key = WikiEntrySubmenu::title) { submenu -> EntrySubmenuCard(submenu, onDetail, Modifier.padding(horizontal = 16.dp)) }
            item { RelationSection(entry.relations, state.packageRoot, onDetail, Modifier.padding(horizontal = 16.dp)) }
        }
    }
}

@Composable
private fun DetailLoading(error: String?, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") }
            Text(if (error == null) "正在加载条目" else "无法加载条目", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 4.dp))
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (error == null) CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) else Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(24.dp))
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun DetailTopBar(name: String, favorite: Boolean, onBack: () -> Unit, onFavorite: () -> Unit) {
    TopAppBar(
        windowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        title = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
        actions = {
            IconButton(onClick = onFavorite) { Icon(if (favorite) Icons.Filled.Star else Icons.Outlined.StarBorder, if (favorite) "取消收藏" else "收藏") }
        },
    )
}

@Composable
private fun DetailHeader(entry: WikiEntry, packageRoot: java.io.File?, modifier: Modifier) {
    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = DetailGreen, shadowElevation = 3.dp) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            entry.image.relativePath()?.let { imagePath ->
                Surface(color = Color(0xFFEFE8D9), shape = MaterialTheme.shapes.medium, modifier = Modifier.size(92.dp)) {
                    EntityImage(imagePath, packageRoot, entry.title, modifier = Modifier.padding(6.dp), categoryLabel = entry.categoryLabel)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(entry.title, style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                entry.englishTitle?.takeUnless { it.trim().equals(entry.title.trim(), ignoreCase = true) }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Color(0xFFC7D8D1), maxLines = 1) }
                Surface(shape = MaterialTheme.shapes.small, color = Color(0xFF2A544B)) { Text(entry.categoryLabel, modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium, color = Color(0xFFE1EEE7)) }
                entry.summary?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Color(0xFFE0E9E3)) }
            }
        }
    }
}

@Composable
private fun EntrySectionCard(section: EntrySection, modifier: Modifier) {
    if (section.facts.isEmpty()) return
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(section.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.ExtraBold)
        Surface(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
                section.facts.forEachIndexed { index, fact ->
                    if (index > 0) androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    FactRow(fact, Modifier.padding(vertical = 9.dp))
                }
            }
        }
    }
}

@Composable
private fun FactRow(fact: EntryFact, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
        Text(fact.label, modifier = Modifier.weight(0.38f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(fact.value, modifier = Modifier.weight(0.62f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EntrySubmenuCard(submenu: WikiEntrySubmenu, onDetail: (String) -> Unit, modifier: Modifier) {
    var expanded by rememberSaveable(submenu.title) { mutableStateOf(submenu.initiallyExpanded) }
    Surface(modifier = modifier.fillMaxWidth().testTag("detail-submenu:${submenu.title}"), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().testTag("detail-submenu-header:${submenu.title}").clickable(role = Role.Button) { expanded = !expanded }.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(submenu.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.ExtraBold)
                    Text(submenu.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(if (expanded) "收起" else "展开", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            if (expanded) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    submenu.groups.forEach { group ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(group.title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                            Text("${group.items.size} 项", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (group.items.isEmpty()) {
                            Text("暂无记录", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else if (submenu.title == "礼物偏好") {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                group.items.forEach { item ->
                                    val target = item.target as? RelationTarget.Entry
                                    AssistChip(onClick = { target?.let { onDetail(it.id) } }, enabled = target != null, label = { Text(item.label) }, modifier = Modifier.testTag("detail-gift-chip:${group.title}:${item.label}"))
                                }
                            }
                        } else if (submenu.title == "日程") {
                            group.items.forEach { item -> ScheduleItemRow(item) }
                        } else {
                            group.items.forEach { item -> SubmenuItemRow(item, onDetail) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScheduleItemRow(item: WikiEntrySubmenuItem) {
    val times = item.details.filter { it.label == "时间" }.map(EntryFact::value)
    val locations = item.details.filter { it.label == "地点" }.map(EntryFact::value)
    val stops = times.zip(locations)
    Surface(modifier = Modifier.fillMaxWidth().testTag("detail-schedule-row:${item.label}"), shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(item.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            stops.forEachIndexed { index, (time, location) ->
                if (index > 0) Text("↓", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = DetailGold)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(time, modifier = Modifier.weight(0.38f), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    Text(location, modifier = Modifier.weight(0.62f), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun SubmenuItemRow(item: WikiEntrySubmenuItem, onDetail: (String) -> Unit) {
    var expanded by rememberSaveable(item.label) { mutableStateOf(false) }
    val target = item.target as? RelationTarget.Entry
    Surface(modifier = Modifier.fillMaxWidth().testTag("detail-submenu-row:${item.label}").clickable(role = Role.Button) { if (target != null) onDetail(target.id) else expanded = !expanded }, shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                if (target != null) Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
            }
            if (expanded || target != null) item.details.forEach { FactRow(it) }
        }
    }
}

@Composable
private fun RelationSection(relations: List<EntryRelation>, packageRoot: java.io.File?, onDetail: (String) -> Unit, modifier: Modifier) {
    if (relations.isEmpty()) return
    val grouped = relations.groupBy(EntryRelation::section)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(if (grouped.size == 1) grouped.keys.first() else "关联内容", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.ExtraBold)
        grouped.forEach { (section, group) ->
            if (grouped.size > 1) Text(section, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            group.forEach { relation -> RelationCard(relation, packageRoot, onDetail) }
        }
    }
}

@Composable
private fun RelationCard(relation: EntryRelation, packageRoot: java.io.File?, onDetail: (String) -> Unit) {
    val entry = relation.target as? RelationTarget.Entry
    val sellPrice = entry?.sellPrice?.takeIf { relation.section == "商品" && relation.label in setOf("商品", "随机商品") }?.let { EntryFact("出售价格", it) }
    val details = relation.details + listOfNotNull(sellPrice)
    Surface(modifier = Modifier.fillMaxWidth().then(if (entry == null) Modifier else Modifier.clickable { onDetail(entry.id) }), shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            entry?.image?.relativePath()?.let { imagePath -> EntityImage(imagePath, packageRoot, entry.title, modifier = Modifier.size(48.dp)) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                if (relation.label != relation.section) Text(relation.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(relation.target.displayText(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                details.forEach { FactRow(it) }
            }
            if (entry != null) Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(17.dp))
        }
    }
}

private fun EntryImage.relativePath(): String? = (this as? EntryImage.Packaged)?.relativePath
private fun RelationTarget.displayText(): String = when (this) {
    is RelationTarget.Entry -> title
    is RelationTarget.ReadableText -> value
    is RelationTarget.Unavailable -> message
}
