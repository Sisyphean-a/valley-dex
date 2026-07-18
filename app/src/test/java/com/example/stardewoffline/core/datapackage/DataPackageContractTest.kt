package com.example.stardewoffline.core.datapackage

import com.example.stardewoffline.core.common.AppError
import com.example.stardewoffline.core.model.DataManifest
import com.example.stardewoffline.core.model.ManifestContent
import com.example.stardewoffline.core.model.ManifestDatabase
import com.example.stardewoffline.core.model.ManifestEntityType
import com.example.stardewoffline.core.model.ManifestQuality
import com.example.stardewoffline.core.model.ManifestTranslationQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DataPackageContractTest {
    @Test
    fun acceptsSupportedManifest() {
        assertNull(DataPackageContract.validateManifest(manifest()))
    }

    @Test
    fun rejectsUnsupportedSchema() {
        val error = DataPackageContract.validateManifest(manifest(schemaVersion = 3))

        assertEquals(AppError.UnsupportedSchema(3), error)
    }

    private fun manifest(schemaVersion: Int = 4) = DataManifest(
        format = "stardew-offline-data",
        schemaVersion = schemaVersion,
        builderVersion = "test",
        gameVersion = "test",
        language = "zh-CN",
        generatedAt = "2026-01-01T00:00:00Z",
        sourceHash = "a".repeat(64),
        publishable = true,
        database = ManifestDatabase("stardew.db", "a".repeat(64)),
        content = ManifestContent(
            entities = 1,
            objects = 1,
            entityTypes = listOf(ManifestEntityType("object", "物品", 1)),
        ),
        quality = ManifestQuality(
            status = DataPackageContract.QUALITY_PASSED,
            translations = ManifestTranslationQuality(complete = 1, missing = 0, invalid = 0, notApplicable = 0, unusable = 0),
            dataErrors = 0,
            unlabeledEntityTypes = emptyList(),
        ),
    )
}
