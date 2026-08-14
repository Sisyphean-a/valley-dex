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
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Chair
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Grass
import androidx.compose.material.icons.filled.Handyman
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PestControl
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SetMeal
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.SwapHoriz
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.stardewoffline.core.model.EntryImage
import com.example.stardewoffline.core.ui.component.EntryImageStatus
import com.example.stardewoffline.core.model.ShopKind
import com.example.stardewoffline.core.model.ShopPresentation
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
            EntryVisual(entry, packageRoot, Modifier.size(54.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(entry.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                englishTitle(entry)?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                EntryMeta(entry, showCategoryLabel)
                entry.actionSummary1?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                entry.actionSummary2?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
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
            EntryVisual(entry, packageRoot, Modifier.fillMaxWidth().aspectRatio(1f))
            Text(entry.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            englishTitle(entry)?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            EntryMeta(entry, showCategoryLabel)
            entry.actionSummary1?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            entry.actionSummary2?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

@Composable
private fun EntryVisual(entry: WikiEntrySummary, packageRoot: File?, modifier: Modifier) {
    val shop = entry.shop
    when {
        entry.image is EntryImage.PackageError || entry.image is EntryImage.Proxy ->
            EntryImageStatus(entry.image, packageRoot, entry.title, entry.categoryLabel, modifier)
        entry.image.relativePath() != null -> EntityImage(entry.image.relativePath(), packageRoot, entry.title, modifier, entry.categoryLabel)
        shop?.owner != null -> EntityImage(shop.owner.image.relativePath(), packageRoot, shop.owner.title, modifier, "村民")
        shop != null -> ShopNatureMark(shop, modifier)
        else -> EntryImageStatus(entry.image, packageRoot, entry.title, entry.categoryLabel, modifier)
    }
}

@Composable
private fun EntryMeta(entry: WikiEntrySummary, showCategoryLabel: Boolean) {
    entry.shop?.let { shop ->
        val text = shop.owner?.let { "店主 · ${it.title}" } ?: shopKindLabel(shop.kind)
        Text("$text · ${shop.offerCount} 件商品", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    } ?: entry.categoryLabel.takeIf { showCategoryLabel && it.isNotBlank() }?.let {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(Icons.Filled.FiberManualRecord, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(7.dp))
            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun ShopNatureMark(shop: ShopPresentation, modifier: Modifier) {
    Surface(modifier = modifier, color = typeMarkColor("商店"), shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(shopKindIcon(shop.kind), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
            Text(shopKindLabel(shop.kind), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun shopKindIcon(kind: ShopKind): ImageVector = when (kind) {
    ShopKind.FESTIVAL -> Icons.Filled.Celebration
    ShopKind.TRAVELING -> Icons.Filled.DirectionsCar
    ShopKind.EXCHANGE -> Icons.Filled.SwapHoriz
    ShopKind.CASINO -> Icons.Filled.Casino
    ShopKind.BOOKSELLER -> Icons.AutoMirrored.Filled.MenuBook
    ShopKind.VOLCANO -> Icons.Filled.LocalFireDepartment
    ShopKind.GENERAL -> Icons.Filled.Storefront
}

private fun shopKindLabel(kind: ShopKind): String = when (kind) {
    ShopKind.FESTIVAL -> "节日"
    ShopKind.TRAVELING -> "旅行"
    ShopKind.EXCHANGE -> "兑换"
    ShopKind.CASINO -> "赌场"
    ShopKind.BOOKSELLER -> "书摊"
    ShopKind.VOLCANO -> "火山"
    ShopKind.GENERAL -> "商店"
}

@Composable
private fun EntryTypeMark(label: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.size(54.dp), color = typeMarkColor(label), shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(entryTypeIcon(label), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun entryTypeIcon(label: String) = when {
    label.matchesAny("作物", "种子", "农场") -> Icons.Filled.Grass
    label.matchesAny("村民", "人物") -> Icons.Filled.People
    label.matchesAny("商店") -> Icons.Filled.Storefront
    label.matchesAny("怪物") -> Icons.Filled.PestControl
    label.matchesAny("鱼") -> Icons.Filled.SetMeal
    label.matchesAny("矿", "宝石") -> Icons.Filled.Diamond
    label.matchesAny("工具") -> Icons.Filled.Build
    label.matchesAny("家具") -> Icons.Filled.Chair
    label.matchesAny("料理") -> Icons.Filled.Restaurant
    label.matchesAny("制作") -> Icons.Filled.Handyman
    label.matchesAny("成就", "任务") -> Icons.Filled.EmojiEvents
    label.matchesAny("武器") -> Icons.Filled.Security
    label.matchesAny("饰品") -> Icons.Filled.Stars
    label.matchesAny("物品") -> Icons.Filled.Inventory2
    label.matchesAny("掉落") -> Icons.Filled.AutoAwesome
    else -> Icons.Filled.Category
}

@Composable
private fun typeMarkColor(label: String) = when {
    label.matchesAny("作物", "鱼", "农场") -> Color(0xFFE1EEE7)
    label.matchesAny("村民", "商店", "任务", "成就") -> Color(0xFFF4E8C9)
    label.matchesAny("怪物", "武器", "掉落") -> Color(0xFFF2E1D8)
    else -> MaterialTheme.colorScheme.surfaceVariant
}

private fun String.matchesAny(vararg values: String): Boolean =
    values.any { contains(it, ignoreCase = true) }

private fun englishTitle(entry: WikiEntrySummary): String? =
    entry.englishTitle?.trim()?.takeIf { it.isNotEmpty() && !it.equals(entry.title.trim(), ignoreCase = true) }

private fun EntryImage.relativePath(): String? = (this as? EntryImage.Packaged)?.relativePath
