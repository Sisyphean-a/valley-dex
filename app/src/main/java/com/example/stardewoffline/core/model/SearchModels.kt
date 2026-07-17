package com.example.stardewoffline.core.model

data class SearchQuery(val original: String, val normalized: String, val tokens: List<String>, val ftsQuery: String?)
data class SearchDocument(val summary: EntitySummary, val pinyin: String?, val initials: String?)
data class SearchResult(val summary: EntitySummary, val score: Int, val reason: String)
