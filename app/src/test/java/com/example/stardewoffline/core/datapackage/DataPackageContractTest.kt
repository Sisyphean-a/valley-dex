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
    fun installManifestRejectsLegacyV4() {
        assertEquals(AppError.UnsupportedSchema(4), DataPackageContract.validateInstallManifest(manifest()))
    }

    @Test
    fun rejectsUnsupportedSchema() {
        val error = DataPackageContract.validateManifest(manifest(schemaVersion = 3))

        assertEquals(AppError.UnsupportedSchema(3), error)
    }

    @Test
    fun acceptsV5ManifestWithRequiredCapabilities() {
        assertNull(DataPackageContract.validateManifest(v5Manifest()))
    }

    @Test
    fun rejectsLegacyV4WhenPresentedAsManifestV2() {
        val error = DataPackageContract.validateManifest(
            v5Manifest(schemaVersion = 4, contentContract = DataPackageContract.CONTENT_CONTRACT),
        )

        assertEquals(AppError.UnsupportedSchema(4), error)
    }

    @Test
    fun rejectsV5ManifestWithMissingRequiredCapability() {
        val error = DataPackageContract.validateManifest(
            v5Manifest(requiredCapabilities = DataPackageContract.requiredV5Capabilities - "search"),
        )

        assertEquals(AppError.InvalidManifest("缺少必需能力"), error)
    }

    @Test
    fun rejectsV5ManifestWithUnknownRequiredCapability() {
        val error = DataPackageContract.validateManifest(
            v5Manifest(requiredCapabilities = DataPackageContract.requiredV5Capabilities + "future-capability"),
        )

        assertEquals(AppError.InvalidManifest("包含未知必需能力"), error)
    }

    @Test
    fun rejectsV5CapabilityDeclaredAsRequiredAndOptional() {
        val error = DataPackageContract.validateManifest(
            v5Manifest().copy(
                capabilities = com.example.stardewoffline.core.model.ManifestCapabilities(
                    required = DataPackageContract.requiredV5Capabilities.toList(),
                    optional = listOf("search"),
                ),
            ),
        )

        assertEquals(AppError.InvalidManifest("capabilities 不能同时声明 required 和 optional"), error)
    }

    @Test
    fun legacyRecoveryContractRemainsSeparateFromInstallContract() {
        assertEquals(AppError.UnsupportedSchema(4), DataPackageContract.validateInstallManifest(manifest()))
        assertNull(DataPackageContract.validateManifest(manifest()))
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

    private fun v5Manifest(
        schemaVersion: Int = DataPackageContract.V5_SCHEMA_VERSION,
        contentContract: String = DataPackageContract.CONTENT_CONTRACT,
        requiredCapabilities: Set<String> = DataPackageContract.requiredV5Capabilities,
    ) = manifest(schemaVersion).copy(
        manifestVersion = DataPackageContract.MANIFEST_VERSION,
        contentContract = contentContract,
        schemaFingerprint = "b".repeat(64),
        database = ManifestDatabase("stardew.db", "a".repeat(64), "b".repeat(64)),
        capabilities = com.example.stardewoffline.core.model.ManifestCapabilities(
            required = requiredCapabilities.toList(),
        ),
    )
}
