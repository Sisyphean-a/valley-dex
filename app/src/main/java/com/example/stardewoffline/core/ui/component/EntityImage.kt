package com.example.stardewoffline.core.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.ImageNotSupported
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import java.io.File

@Composable
fun EntityImage(
    imagePath: String?,
    packageRoot: File?,
    name: String,
    modifier: Modifier = Modifier,
    categoryLabel: String? = null,
) {
    val image = imagePath?.let { path -> packageRoot?.let { root -> resolvePackagedImage(root, path) } }
    var failed by remember(imagePath, packageRoot) { mutableStateOf(false) }
    if (image == null || failed) {
        MissingImage(name, categoryLabel, modifier)
    } else {
        AsyncImage(
            model = image,
            contentDescription = name,
            modifier = modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
            filterQuality = FilterQuality.None,
            onError = { failed = true },
        )
    }
}

@Composable
fun EntryImageStatus(
    status: com.example.stardewoffline.core.model.EntryImage,
    packageRoot: File?,
    name: String,
    categoryLabel: String?,
    modifier: Modifier = Modifier,
) {
    when (status) {
        is com.example.stardewoffline.core.model.EntryImage.Packaged ->
            EntityImage(status.relativePath, packageRoot, name, modifier, categoryLabel)
        com.example.stardewoffline.core.model.EntryImage.Proxy ->
            StatusImage(name, "${name} 使用展示代理视觉", categoryLabel, modifier)
        com.example.stardewoffline.core.model.EntryImage.PackageError ->
            StatusImage(name, "${name} 图片异常", categoryLabel, modifier, error = true)
        com.example.stardewoffline.core.model.EntryImage.Missing ->
            StatusImage(name, "$name 暂无图片", categoryLabel, modifier)
    }
}

@Composable
private fun StatusImage(
    name: String,
    description: String,
    categoryLabel: String?,
    modifier: Modifier,
    error: Boolean = false,
) {
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surfaceVariant) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (error) Icons.Outlined.Warning else missingImageIcon(categoryLabel),
                contentDescription = description,
                tint = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MissingImage(name: String, categoryLabel: String?, modifier: Modifier) {
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surfaceVariant) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = missingImageIcon(categoryLabel),
                contentDescription = "$name 暂无图片",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun missingImageIcon(categoryLabel: String?) = when {
    categoryLabel.isNullOrBlank() -> Icons.Outlined.ImageNotSupported
    categoryLabel.containsAny("商店", "shop") -> Icons.Outlined.Storefront
    categoryLabel.containsAny("日程", "schedule") -> Icons.Outlined.CalendarMonth
    categoryLabel.containsAny("礼物", "gift") -> Icons.Outlined.CardGiftcard
    categoryLabel.containsAny("人物", "村民", "villager", "npc") -> Icons.Outlined.Person
    else -> Icons.Outlined.ImageNotSupported
}

private fun String.containsAny(vararg values: String): Boolean =
    values.any { contains(it, ignoreCase = true) }

/** The package validator already proves file existence; composition only retains the path boundary check. */
private fun resolvePackagedImage(root: File, imagePath: String): File? {
    val rootPath = root.toPath().normalize()
    val path = rootPath.resolve(imagePath).normalize()
    return path.takeIf { it.startsWith(rootPath) }?.toFile()
}
