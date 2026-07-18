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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.stardewoffline.core.model.DetailFact
import com.example.stardewoffline.core.model.DetailRelation
import com.example.stardewoffline.core.model.DetailRelationGroup
import com.example.stardewoffline.core.ui.component.EntityImage
import com.example.stardewoffline.core.ui.LocalAppPreferences

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
    val entity = state.entity
    val settings = LocalAppPreferences.current
    if (entity == null) {
        DetailLoading(state.error)
        return
    }
    val displayName = entity.nameZh.ifBlank { entity.nameEn ?: entity.internalName ?: entity.gameId ?: entity.id }
    Scaffold(topBar = { DetailTopBar(displayName, favorite, onBack, onFavorite) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = padding,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { DetailHeader(state, displayName, settings.showEnglishName, Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }
            state.presentation?.facts?.takeIf { it.isNotEmpty() }?.let { facts ->
                item { FactSection("核心信息", facts, Modifier.padding(horizontal = 16.dp)) }
            }
            state.presentation?.relationGroups?.forEach { group ->
                item { RelationSection(group, state.targets, onDetail, Modifier.padding(horizontal = 16.dp)) }
            }
            state.aliases.takeIf { it.isNotEmpty() }?.let { aliases ->
                item { FactSection("别名", listOf(DetailFact("别名", aliases.joinToString("、"))), Modifier.padding(horizontal = 16.dp)) }
            }
            item { NoteSection(note, onSaveNote, Modifier.padding(horizontal = 16.dp)) }
            state.presentation?.rawJson?.takeIf { settings.showTechnicalFields }?.let { raw ->
                item { RawDataSection(raw, Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) }
            }
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
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
        },
        actions = {
            IconButton(onClick = onFavorite) {
                val icon = if (favorite) Icons.Filled.Star else Icons.Outlined.StarBorder
                Icon(icon, contentDescription = if (favorite) "取消收藏" else "收藏")
            }
        },
    )
}

@Composable
private fun DetailHeader(state: DetailUiState, displayName: String, showEnglish: Boolean, modifier: Modifier) {
    val entity = requireNotNull(state.entity)
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        EntityImage(entity.imagePath, state.packageRoot, displayName, Modifier.size(104.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(displayName, style = MaterialTheme.typography.headlineSmall)
            if (showEnglish) entity.nameEn?.takeIf(String::isNotBlank)?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
            Text(listOfNotNull(entity.entityType, entity.category).joinToString(" · "), style = MaterialTheme.typography.labelLarge)
            entity.descriptionZh?.takeIf(String::isNotBlank)?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            if (entity.descriptionZh.isNullOrBlank() && !entity.descriptionEn.isNullOrBlank()) {
                Text("暂无中文描述", style = MaterialTheme.typography.labelMedium)
                Text(entity.descriptionEn, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun FactSection(title: String, facts: List<DetailFact>, modifier: Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionTitle(title)
        facts.forEach { fact -> FactRow(fact) }
    }
}

@Composable
private fun FactRow(fact: DetailFact) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(fact.label, modifier = Modifier.weight(0.38f), style = MaterialTheme.typography.labelLarge)
        Text(fact.value, modifier = Modifier.weight(0.62f), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun RelationSection(
    group: DetailRelationGroup,
    targets: Map<String, com.example.stardewoffline.core.model.EntitySummary>,
    onDetail: (String) -> Unit,
    modifier: Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(group.title)
        group.relations.forEach { relation -> RelationRow(relation, targets[relation.targetId], onDetail) }
    }
}

@Composable
private fun RelationRow(
    relation: DetailRelation,
    target: com.example.stardewoffline.core.model.EntitySummary?,
    onDetail: (String) -> Unit,
) {
    val clickModifier = if (target == null) Modifier else Modifier.clickable { onDetail(target.id) }
    Column(clickModifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val name = target?.nameZh ?: relation.targetId ?: relation.label
        Text("${relation.label}：$name", style = MaterialTheme.typography.titleSmall)
        target?.let { Text(listOfNotNull(it.entityType, it.category).joinToString(" · "), style = MaterialTheme.typography.labelMedium) }
        if (target == null && relation.targetId != null) Text("当前数据包中未找到关联实体", style = MaterialTheme.typography.labelMedium)
        relation.details.forEach { FactRow(it) }
        HorizontalDivider()
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
private fun RawDataSection(raw: String, modifier: Modifier) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedButton(onClick = { expanded = !expanded }) { Text(if (expanded) "收起原始数据" else "查看原始数据") }
        if (expanded) Text(raw, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium)
}
