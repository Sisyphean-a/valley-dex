package com.example.stardewoffline.data

import com.example.stardewoffline.core.common.getOrNull
import com.example.stardewoffline.core.model.DetailRelation
import com.example.stardewoffline.core.model.EntityDetail
import com.example.stardewoffline.core.model.EntitySummary
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EntityRelationResolver @Inject constructor(
    private val content: ContentRepository,
) {
    suspend fun resolve(relations: List<DetailRelation>): Map<String, EntitySummary> {
        val candidates = candidatesFor(relations)
        val summaries = content.summaries(candidates.values.flatten().distinct()).getOrNull().orEmpty()
        return candidates.mapNotNull { (rawId, ids) ->
            ids.firstNotNullOfOrNull(summaries::get)?.let { rawId to it }
        }.toMap()
    }

    suspend fun resolveDetails(relations: List<DetailRelation>): Map<String, EntityDetail> {
        val candidates = candidatesFor(relations)
        val details = content.detailsByIds(candidates.values.flatten().distinct()).getOrNull().orEmpty().associateBy(EntityDetail::id)
        return candidates.mapNotNull { (rawId, ids) ->
            ids.firstNotNullOfOrNull(details::get)?.let { rawId to it }
        }.toMap()
    }

    private fun candidatesFor(relations: List<DetailRelation>): Map<String, List<String>> =
        relations.mapNotNull(DetailRelation::targetId).associateWith(::relationCandidates)

}

internal fun relationCandidates(rawId: String): List<String> {
    val value = rawId.trim()
    if (value.contains(':')) return listOf(value)
    qualifiedItem(value)?.let { return listOf(it) }
    if (value.all(Char::isDigit)) return NUMERIC_TYPES.map { "$it:$value" }
    return NAMED_TYPES.map { "$it:$value" }
}

private fun qualifiedItem(value: String): String? {
    val match = ITEM_REFERENCE.matchEntire(value) ?: return null
    val type = ITEM_TYPES[match.groupValues[1]] ?: return null
    return "$type:${match.groupValues[2]}"
}

private val ITEM_REFERENCE = Regex("^\\((O|BC|F|T|TR|W|B)\\)(.+)$")
private val ITEM_TYPES = mapOf(
    "O" to "object", "BC" to "big_craftable", "F" to "furniture",
    "T" to "tool", "TR" to "trinket", "W" to "weapon", "B" to "footwear",
)
private val NUMERIC_TYPES = listOf("object", "mineral", "ring", "crop", "fish", "weapon", "footwear")
private val NAMED_TYPES = listOf(
    "object",
    "mineral",
    "ring",
    "crop",
    "fish",
    "big_craftable",
    "furniture",
    "footwear",
    "trinket",
    "villager",
    "monster",
    "shop",
    "tool",
    "weapon",
    "cooking_recipe",
    "crafting_recipe",
)
