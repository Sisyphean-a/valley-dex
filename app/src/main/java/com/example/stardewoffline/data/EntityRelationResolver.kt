package com.example.stardewoffline.data

import com.example.stardewoffline.core.common.getOrNull
import com.example.stardewoffline.core.model.DetailRelation
import com.example.stardewoffline.core.model.EntitySummary
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EntityRelationResolver @Inject constructor(
    private val content: ContentRepository,
) {
    suspend fun resolve(relations: List<DetailRelation>): Map<String, EntitySummary> {
        val candidates = relations.mapNotNull(DetailRelation::targetId)
            .associateWith(::relationCandidates)
        val summaries = content.summaries(candidates.values.flatten().distinct()).getOrNull().orEmpty()
        return candidates.mapNotNull { (rawId, ids) ->
            ids.firstNotNullOfOrNull(summaries::get)?.let { rawId to it }
        }.toMap()
    }

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

private val ITEM_REFERENCE = Regex("^\\((O|BC|F|T|TR|W)\\)(.+)$")
private val ITEM_TYPES = mapOf(
    "O" to "object", "BC" to "big_craftable", "F" to "furniture",
    "T" to "tool", "TR" to "trinket", "W" to "weapon",
)
private val NUMERIC_TYPES = listOf("object", "mineral", "ring", "crop", "fish", "weapon", "footwear")
private val NAMED_TYPES = listOf("villager", "monster", "shop", "tool", "weapon", "cooking_recipe", "crafting_recipe")
