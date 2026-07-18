package com.example.stardewoffline.feature.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.stardewoffline.core.model.EntryFact
import com.example.stardewoffline.core.model.EntryImage
import com.example.stardewoffline.core.model.EntryRelation
import com.example.stardewoffline.core.model.EntrySection
import com.example.stardewoffline.core.model.RelationTarget
import com.example.stardewoffline.core.model.WikiEntry
import com.example.stardewoffline.core.ui.component.EntityImage

@Composable
fun DetailScreen(
    state: DetailUiState,
    favorite: Boolean,
    note: String,
    onBack: () -> Unit,
    onFavorite: () -> Unit,
    onSaveNote: (String) -> Unit,
    onDetail: (String) -> Unit,
) {
    val entry = state.entry
    if (entry == null) {
        DetailLoading(state.error)
        return
    }
    Scaffold(topBar = { DetailTopBar(entry.title, favorite, onBack, onFavorite) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = padding,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { DetailHeader(entry, state.packageRoot, Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) }
            items(entry.sections, key = EntrySection::title) { section ->
                EntrySectionCard(section, Modifier.padding(horizontal = 20.dp))
            }
            item { RelationSection(entry.relations, onDetail, Modifier.padding(horizontal = 20.dp)) }
            item { NoteSection(note, onSaveNote, Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) }
        }
    }
}

@Composable
private fun DetailLoading(error: String?) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (error == null) CircularProgressIndicator() else Text(error)
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun DetailTopBar(name: String, favorite: Boolean, onBack: () -> Unit, onFavorite: () -> Unit) {
    TopAppBar(
        title = { Text(name, maxLines = 1) },
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
        actions = {
            IconButton(onClick = onFavorite) {
                Icon(if (favorite) Icons.Filled.Star else Icons.Outlined.StarBorder, if (favorite) "取消收藏" else "收藏")
            }
        },
    )
}

@Composable
private fun DetailHeader(entry: WikiEntry, packageRoot: java.io.File?, modifier: Modifier) {
    Card(modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Row(Modifier.padding(20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            EntityImage(
                imagePath = entry.image.relativePath(),
                packageRoot = packageRoot,
                name = entry.title,
                modifier = Modifier.size(108.dp),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(entry.title, style = MaterialTheme.typography.headlineSmall)
                entry.englishTitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
                    Text(entry.categoryLabel, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                }
                entry.summary?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }
}

@Composable
private fun EntrySectionCard(section: EntrySection, modifier: Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(section.title)
        Card(modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                section.facts.forEach { FactRow(it) }
            }
        }
    }
}

@Composable
private fun FactRow(fact: EntryFact) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(fact.label, modifier = Modifier.weight(0.38f), style = MaterialTheme.typography.labelLarge)
        Text(fact.value, modifier = Modifier.weight(0.62f), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun RelationSection(relations: List<EntryRelation>, onDetail: (String) -> Unit, modifier: Modifier) {
    if (relations.isEmpty()) return
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("关联")
        relations.groupBy(EntryRelation::section).forEach { (section, group) ->
            Text(section, style = MaterialTheme.typography.labelLarge)
            group.forEach { relation -> RelationCard(relation, onDetail) }
        }
    }
}

@Composable
private fun RelationCard(relation: EntryRelation, onDetail: (String) -> Unit) {
    val entry = relation.target as? RelationTarget.Entry
    Card(Modifier.fillMaxWidth().then(if (entry == null) Modifier else Modifier.clickable { onDetail(entry.id) })) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(relation.label, style = MaterialTheme.typography.labelLarge)
            Text(relation.target.displayText(), style = MaterialTheme.typography.titleMedium)
            relation.details.forEach { FactRow(it) }
        }
    }
}

@Composable
private fun NoteSection(note: String, onSave: (String) -> Unit, modifier: Modifier) {
    var draft by rememberSaveable(note) { mutableStateOf(note) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("个人笔记")
        OutlinedTextField(
            value = draft,
            onValueChange = { if (it.length <= 5000) draft = it },
            modifier = Modifier.fillMaxWidth().heightIn(min = 112.dp),
            label = { Text("最多 5000 个字符") },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onSave(draft) }) { Text("保存笔记") }
            if (note.isNotBlank()) OutlinedButton(onClick = { confirmDelete = true }) { Text("删除") }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除笔记") },
            text = { Text("删除后无法恢复。") },
            confirmButton = { TextButton(onClick = { draft = ""; onSave(""); confirmDelete = false }) { Text("删除") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge)
}

private fun EntryImage.relativePath(): String? = (this as? EntryImage.Packaged)?.relativePath

private fun RelationTarget.displayText(): String = when (this) {
    is RelationTarget.Entry -> title
    is RelationTarget.ReadableText -> value
    is RelationTarget.Unavailable -> message
}
