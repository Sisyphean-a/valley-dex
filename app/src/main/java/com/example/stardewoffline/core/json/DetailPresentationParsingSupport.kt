package com.example.stardewoffline.core.json

import com.example.stardewoffline.core.model.DetailFact
import com.example.stardewoffline.core.model.DetailRelation
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

internal fun legacyFields(raw: JsonObject, type: String): List<String> {
    val values = raw.array("legacyFields").texts()
    if (values.isNotEmpty()) {
        if (values.size == 1 && type == "achievement" && values[0].contains('^')) return values[0].split('^')
        return values
    }
    val legacy = raw.string("legacyValue") ?: return emptyList()
    return legacy.split(if (type == "achievement") '^' else '/').toList()
}

internal fun bundleEntries(raw: JsonObject): List<JsonObject> = buildList {
    addAll(raw.array("Bundles").mapNotNull { it.asObject() })
    raw.array("BundleSets").forEach { set -> addAll(set.asObject()?.array("Bundles")?.mapNotNull { it.asObject() }.orEmpty()) }
}

internal fun bundleItemTokens(value: String?): List<Triple<String, Int, Int>> {
    val tokens = value?.split(Regex("\\s+"))?.filter(String::isNotBlank).orEmpty()
    return buildList {
        var index = 0
        while (index + 2 < tokens.size) {
            val id = tokens[index]
            val quantity = tokens[index + 1].toIntOrNull()
            val quality = tokens[index + 2].toIntOrNull()
            if (quantity != null && quality != null) add(Triple(id, quantity, quality))
            index += 3
        }
    }
}

internal fun isScheduleEntry(value: String): Boolean = scheduleTime(value.split(Regex("\\s+")).firstOrNull()) != null

internal fun scheduleTime(token: String?): Int? = token?.removePrefix("a")?.toIntOrNull()

internal fun entityTypeName(value: String): String = when (value) {
    "object" -> "物品"
    "big_craftable" -> "大型工艺品"
    "weapon" -> "武器"
    else -> value
}

internal fun JsonObject.relation(key: String, label: String) = string(key)?.let { DetailRelation(label, it) }

internal fun JsonObject.fact(key: String, label: String, transform: (JsonPrimitive) -> String? = { it.contentOrNull }) = primitive(key)?.let(transform)?.takeIf(String::isNotBlank)?.let { DetailFact(label, it) }

internal fun JsonObject.string(key: String) = primitive(key)?.contentOrNull

internal fun JsonObject.stringAny(vararg keys: String) = keys.asSequence()
    .mapNotNull { key -> (this[key] as? JsonPrimitive)?.contentOrNull }
    .firstOrNull()

internal fun JsonObject.primitive(key: String): JsonPrimitive? = this[key] as? JsonPrimitive

internal fun JsonObject.array(key: String) = this[key] as? JsonArray ?: JsonArray(emptyList())

internal fun JsonObject.arrayAny(vararg keys: String) = keys.asSequence().mapNotNull { key -> this[key] as? JsonArray }.firstOrNull() ?: JsonArray(emptyList())

internal fun JsonObject.objectAt(key: String) = this[key] as? JsonObject ?: JsonObject(emptyMap())

internal fun JsonElement.asObject() = this as? JsonObject

internal fun JsonElement.asPrimitive() = this as? JsonPrimitive

internal fun JsonElement.asArray() = this as? JsonArray

internal fun JsonArray.texts() = mapNotNull { it.asPrimitive()?.contentOrNull }

internal fun JsonObject.intOrNull(key: String) = string(key)?.toIntOrNull()

internal fun String.capitalized() = replaceFirstChar { it.uppercase() }
