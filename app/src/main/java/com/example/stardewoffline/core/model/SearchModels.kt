package com.example.stardewoffline.core.model

internal data class SearchQuery(val original: String, val normalized: String, val tokens: List<String>, val ftsQuery: String?)
internal data class SearchDocument(val summary: EntitySummary, val pinyin: String?, val initials: String?)
internal data class SearchResult(val summary: EntitySummary, val score: Int, val reason: String)
