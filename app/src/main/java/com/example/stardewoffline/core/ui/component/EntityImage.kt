package com.example.stardewoffline.core.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.ImageNotSupported
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import java.io.File
import java.nio.file.Files

@Composable
fun EntityImage(
    imagePath: String?,
    packageRoot: File?,
    name: String,
    modifier: Modifier = Modifier,
    categoryLabel: String? = null,
) {
    val image = imagePath?.let { path -> packageRoot?.let { root -> safeImage(root, path) } }
    if (image == null) {
        Surface(modifier = modifier, color = MaterialTheme.colorScheme.surfaceVariant) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = missingImageIcon(categoryLabel),
                    contentDescription = "$name 暂无图片",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    } else {
        AsyncImage(
            model = image,
            contentDescription = name,
            modifier = modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
            filterQuality = FilterQuality.None,
        )
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

private fun safeImage(root: File, imagePath: String): File? {
    val rootPath = root.toPath().normalize()
    val path = rootPath.resolve(imagePath).normalize()
    return path.takeIf { it.startsWith(rootPath) && Files.isRegularFile(it) }?.toFile()
}
