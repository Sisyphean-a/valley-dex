package com.example.stardewoffline.core.datapackage

import com.example.stardewoffline.core.common.AppError
import com.example.stardewoffline.core.model.DataManifest
import com.example.stardewoffline.core.model.ManifestContent
import com.example.stardewoffline.core.model.ManifestDatabase
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

    private fun manifest(schemaVersion: Int = 2) = DataManifest(
        format = "stardew-offline-data",
        schemaVersion = schemaVersion,
        builderVersion = "test",
        gameVersion = "test",
        language = "zh-CN",
        generatedAt = "2026-01-01T00:00:00Z",
        database = ManifestDatabase("stardew.db", "a".repeat(64)),
        content = ManifestContent(entities = 1),
    )
}
