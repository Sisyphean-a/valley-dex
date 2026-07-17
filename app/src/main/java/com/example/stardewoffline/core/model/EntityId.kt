package com.example.stardewoffline.core.model

@JvmInline
value class EntityId(val value: String) {
    val typePrefix: String?
        get() = value.substringBefore(':', missingDelimiterValue = "").ifBlank { null }

    val sourceId: String?
        get() = value.substringAfter(':', missingDelimiterValue = "").ifBlank { null }
}
