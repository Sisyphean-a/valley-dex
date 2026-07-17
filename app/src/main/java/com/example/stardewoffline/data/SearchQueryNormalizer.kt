package com.example.stardewoffline.data

import com.example.stardewoffline.core.model.SearchQuery
import java.text.Normalizer
import java.util.Locale

object SearchQueryNormalizer {
    fun normalize(raw: String): SearchQuery? {
        val normalized = Normalizer.normalize(raw, Normalizer.Form.NFKC)
            .trim().replace(Regex("\\s+"), " ").lowercase(Locale.ROOT).take(64)
        val tokens = normalized.split(' ').filter(String::isNotBlank).take(8)
        if (tokens.isEmpty()) return null
        val ftsTokens = tokens.map { it.replace(Regex("[^\\p{IsHan}\\p{L}\\p{N}]"), "") }.filter(String::isNotBlank)
        return SearchQuery(raw, tokens.joinToString(" "), tokens, ftsTokens.takeIf(List<String>::isNotEmpty)?.joinToString(" ") { "$it*" })
    }

    fun escapeLike(value: String): String = value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
}
