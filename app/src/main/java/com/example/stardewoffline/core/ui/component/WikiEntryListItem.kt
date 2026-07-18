package com.example.stardewoffline.core.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.stardewoffline.core.model.EntryImage
import com.example.stardewoffline.core.model.WikiEntrySummary
import java.io.File

@Composable
fun WikiEntryListItem(
    entry: WikiEntrySummary,
    packageRoot: File?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(modifier = modifier.fillMaxWidth().clickable(onClick = onClick), tonalElevation = 1.dp) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EntityImage(
                imagePath = entry.image.relativePath(),
                packageRoot = packageRoot,
                name = entry.title,
                modifier = Modifier.size(48.dp),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(entry.title, style = MaterialTheme.typography.titleMedium)
                entry.englishTitle?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                entry.categoryLabel.takeIf(String::isNotBlank)?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
            }
        }
    }
}

private fun EntryImage.relativePath(): String? = (this as? EntryImage.Packaged)?.relativePath
