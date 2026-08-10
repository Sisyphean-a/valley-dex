package com.example.stardewoffline.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.stardewoffline.core.model.EntryImage
import com.example.stardewoffline.core.model.WikiEntrySummary
import java.io.File

@Composable
fun WikiEntryListItem(
    entry: WikiEntrySummary,
    packageRoot: File?,
    modifier: Modifier = Modifier,
    showCategoryLabel: Boolean = true,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth().semantics { contentDescription = "打开 ${entry.title}" }.clickable(role = Role.Button, onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = CardDefaults.outlinedCardBorder(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            entry.image.relativePath()?.let { imagePath ->
                EntityImage(imagePath, packageRoot, entry.title, modifier = Modifier.size(54.dp), categoryLabel = entry.categoryLabel)
            } ?: EntryTypeMark(entry.categoryLabel)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(entry.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                englishTitle(entry)?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                entry.categoryLabel.takeIf { showCategoryLabel && it.isNotBlank() }?.let {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Filled.FiberManualRecord, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(7.dp))
                        Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun WikiEntryGridItem(
    entry: WikiEntrySummary,
    packageRoot: File?,
    modifier: Modifier = Modifier,
    showCategoryLabel: Boolean = true,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().testTag("wiki-grid-card:${entry.id}").semantics { contentDescription = "打开 ${entry.title}" },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            entry.image.relativePath()?.let { imagePath ->
                EntityImage(imagePath, packageRoot, entry.title, modifier = Modifier.fillMaxWidth().aspectRatio(1f), categoryLabel = entry.categoryLabel)
            } ?: EntryTypeMark(entry.categoryLabel, Modifier.fillMaxWidth().aspectRatio(1f))
            Text(entry.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            englishTitle(entry)?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            entry.categoryLabel.takeIf { showCategoryLabel && it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun EntryTypeMark(label: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.size(54.dp), color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(label.take(1), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.ExtraBold)
            Text("资料", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun englishTitle(entry: WikiEntrySummary): String? =
    entry.englishTitle?.trim()?.takeIf { it.isNotEmpty() && !it.equals(entry.title.trim(), ignoreCase = true) }

private fun EntryImage.relativePath(): String? = (this as? EntryImage.Packaged)?.relativePath
