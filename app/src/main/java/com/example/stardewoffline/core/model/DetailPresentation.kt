package com.example.stardewoffline.core.model

data class DetailFact(
    val label: String,
    val value: String,
)

data class DetailRelation(
    val label: String,
    val targetId: String?,
    val details: List<DetailFact> = emptyList(),
)

data class DetailRelationGroup(
    val title: String,
    val relations: List<DetailRelation>,
)

data class DetailPresentation(
    val facts: List<DetailFact>,
    val relationGroups: List<DetailRelationGroup>,
)
