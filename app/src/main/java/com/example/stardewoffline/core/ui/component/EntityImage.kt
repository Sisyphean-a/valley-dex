package com.example.stardewoffline.core.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import coil3.compose.AsyncImage
import java.io.File
import java.nio.file.Files

@Composable
fun EntityImage(
    imagePath: String?,
    packageRoot: File?,
    name: String,
    modifier: Modifier = Modifier,
) {
    val image = imagePath?.let { path -> packageRoot?.let { root -> safeImage(root, path) } }
    if (image == null) {
        Surface(modifier = modifier, color = MaterialTheme.colorScheme.surfaceVariant) {
            Box(contentAlignment = Alignment.Center) {
                Text("图", style = MaterialTheme.typography.titleLarge)
            }
        }
    } else {
        AsyncImage(model = image, contentDescription = name, modifier = modifier.fillMaxSize())
    }
}

private fun safeImage(root: File, imagePath: String): File? {
    val rootPath = root.toPath().normalize()
    val path = rootPath.resolve(imagePath).normalize()
    return path.takeIf { it.startsWith(rootPath) && Files.isRegularFile(it) }?.toFile()
}
