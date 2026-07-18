package com.example.stardewoffline.core.datapackage

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.stardewoffline.core.common.AppResult
import com.example.stardewoffline.core.common.HashUtils
import com.example.stardewoffline.core.database.content.ContentDatabaseFactory
import com.example.stardewoffline.core.model.SearchQuery
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RealDataPackageValidationTest {
    @Test
    fun validatesBundledSchema2DataPackage() {
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val root = File(context.cacheDir, "real-package-test")
            root.deleteRecursively()
            root.mkdirs()
            val archive = File(root, "stardew.svdata")
            context.assets.open("default-data/stardew-zh-cn.svdata").use { input ->
                archive.outputStream().use(input::copyTo)
            }
            val extracted = File(root, "extracted")
            val extractor = SafeZipExtractor(Dispatchers.IO)
            assertTrue(extractor.extract(archive, extracted) is AppResult.Success)

            val validator = DataPackageValidator(
                json = Json { ignoreUnknownKeys = true; explicitNulls = false; isLenient = false },
                hashUtils = HashUtils(Dispatchers.IO),
                databaseFactory = ContentDatabaseFactory(Dispatchers.IO),
                ioDispatcher = Dispatchers.IO,
            )
            val result = validator.validate(extracted)

            assertTrue(result is AppResult.Success)
            val info = (result as AppResult.Success).value
            assertEquals(2, info.buildMeta.schemaVersion)
            assertEquals(3688, info.buildMeta.entityCount)
            assertEquals("zh-CN", info.buildMeta.locale)
            val content = (ContentDatabaseFactory(Dispatchers.IO).open(extracted, File(extracted, "stardew.db")) as AppResult.Success).value
            try {
                val search = content.searchPrefix(SearchQuery("fangfengcao", "fangfengcao", listOf("fangfengcao"), null), 10)
                assertTrue((search as AppResult.Success).value.any { it.summary.id == "object:24" })
            } finally {
                content.close()
            }
            root.deleteRecursively()
        }
    }
}
