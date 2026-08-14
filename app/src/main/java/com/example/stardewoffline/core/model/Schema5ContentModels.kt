package com.example.stardewoffline.core.model

/** Typed rows exposed by the schema-5 player-facts-v1 content boundary. */
enum class Schema5FactStatus {
    FIXED,
    CONDITIONAL,
    DYNAMIC_RULE,
    UNKNOWN,
    NOT_COLLECTED,
    NOT_APPLICABLE,
}

enum class Schema5ValueType { TEXT, INTEGER, REAL, BOOLEAN, RANGE }

enum class Schema5ConditionCompleteness { COMPLETE, PARTIAL, OPAQUE }

enum class Schema5VisualStatus {
    OFFICIAL_OWN,
    OFFICIAL_REUSE,
    OFFICIAL_NONE,
    PROXY,
    PENDING_REVIEW,
    PACKAGE_ERROR,
}

data class Schema5TypedValue(
    val type: Schema5ValueType,
    val text: String? = null,
    val integer: Long? = null,
    val real: Double? = null,
    val boolean: Boolean? = null,
    val unit: String? = null,
    val rangeMin: Double? = null,
    val rangeMax: Double? = null,
) {
    fun display(): String = when (type) {
        Schema5ValueType.TEXT -> text.orEmpty()
        Schema5ValueType.INTEGER -> integer?.toString().orEmpty()
        Schema5ValueType.REAL -> real?.toString().orEmpty()
        Schema5ValueType.BOOLEAN -> when (boolean) {
            true -> "是"
            false -> "否"
            null -> "暂未提供"
        }
        Schema5ValueType.RANGE -> listOfNotNull(rangeMin?.toString(), rangeMax?.toString()).joinToString("–")
    }
}

data class Schema5SourceSummary(
    val kind: String,
    val title: String,
    val gameVersion: String?,
    val revision: String?,
    val sourceUrl: String?,
    val reviewedAt: String?,
    val evidenceKind: String? = null,
    val transformationRule: String? = null,
    val reviewStatus: String? = null,
    val conflictStatus: String? = null,
    val expiresAt: String? = null,
)

data class Schema5Condition(
    val id: String,
    val completeness: Schema5ConditionCompleteness,
    val playerSummary: String?,
    val originalText: String?,
)

data class Schema5FactItem(
    val id: String,
    val slotId: String,
    val ordinal: Int,
    val value: Schema5TypedValue,
    val scopeId: String?,
    val condition: Schema5Condition?,
    val sources: List<Schema5SourceSummary> = emptyList(),
)

data class Schema5Fact(
    val id: String,
    val entityId: String,
    val slotKey: String,
    val status: Schema5FactStatus,
    val value: Schema5TypedValue?,
    val condition: Schema5Condition?,
    val sources: List<Schema5SourceSummary> = emptyList(),
    val items: List<Schema5FactItem> = emptyList(),
)

data class Schema5Relation(
    val id: String,
    val relationGroupId: String,
    val subjectEntityId: String,
    val predicate: String,
    val objectEntityId: String,
    val originalDirection: String,
    val label: String?,
    val condition: Schema5Condition?,
    val sources: List<Schema5SourceSummary> = emptyList(),
    val family: String? = null,
)

data class Schema5RelationGroup(
    val id: String,
    val entityId: String,
    val family: String,
    val status: Schema5FactStatus,
    val condition: Schema5Condition?,
    val relations: List<Schema5Relation>,
)

data class Schema5Visual(
    val id: String,
    val entityId: String,
    val role: String,
    val status: Schema5VisualStatus,
    val relativePath: String?,
    val sha256: String?,
    val sourceEntityId: String?,
    val cropRect: String?,
    val ruleVersion: String?,
    val reuseReason: String?,
)

data class Schema5EntityCard(
    val entityId: String,
    val identitySummary: String?,
    val actionSummary1: String?,
    val actionSummary2: String?,
    val categoryLabel: String?,
    val sortKey: String,
)

data class Schema5Facet(
    val id: String,
    val groupId: String,
    val scopeFamily: String,
    val scopeId: String,
    val valueType: Schema5ValueType,
    val value: Schema5TypedValue,
    val claimStatus: Schema5FactStatus,
    val condition: Schema5Condition?,
    val sources: List<Schema5SourceSummary> = emptyList(),
)

data class Schema5EntitySummary(
    val id: String,
    val entityType: String,
    val gameId: String?,
    val internalName: String?,
    val nameZh: String,
    val nameEn: String?,
    val descriptionZh: String?,
    val descriptionEn: String?,
    val category: String?,
    val translationStatus: TranslationStatus,
    val card: Schema5EntityCard,
    val visual: Schema5Visual?,
    val facets: List<Schema5Facet>,
)

data class Schema5EntityDetail(
    val summary: Schema5EntitySummary,
    val createdAt: String,
    val aliases: List<String>,
    val facts: List<Schema5Fact>,
    val relationGroups: List<Schema5RelationGroup>,
    val visuals: List<Schema5Visual>,
) {
    val id: String get() = summary.id
    val entityType: String get() = summary.entityType
    val nameZh: String get() = summary.nameZh
}

data class Schema5SearchDocument(
    val summary: Schema5EntitySummary,
    val nameZh: String,
    val nameEn: String?,
    val aliases: String,
    val keywords: String,
    val actionSummaries: String,
)

data class Schema5SearchResult(
    val summary: Schema5EntitySummary,
    val score: Int,
    val reason: String,
)

data class Schema5BrowsePage(
    val summaries: Map<String, List<Schema5EntitySummary>>,
    val nextCursor: String?,
)

data class Schema5SearchPage(
    val results: List<Schema5SearchResult>,
    val nextCursor: String?,
)

internal fun schema5FactStatus(raw: String): Schema5FactStatus = when (raw) {
    "fixed" -> Schema5FactStatus.FIXED
    "conditional" -> Schema5FactStatus.CONDITIONAL
    "dynamic_rule" -> Schema5FactStatus.DYNAMIC_RULE
    "unknown" -> Schema5FactStatus.UNKNOWN
    "not_collected" -> Schema5FactStatus.NOT_COLLECTED
    "not_applicable" -> Schema5FactStatus.NOT_APPLICABLE
    else -> Schema5FactStatus.UNKNOWN
}

internal fun schema5ValueType(raw: String?): Schema5ValueType? = when (raw) {
    "text" -> Schema5ValueType.TEXT
    "integer" -> Schema5ValueType.INTEGER
    "real" -> Schema5ValueType.REAL
    "boolean" -> Schema5ValueType.BOOLEAN
    "range" -> Schema5ValueType.RANGE
    else -> null
}

internal fun schema5ConditionCompleteness(raw: String): Schema5ConditionCompleteness = when (raw) {
    "complete" -> Schema5ConditionCompleteness.COMPLETE
    "partial" -> Schema5ConditionCompleteness.PARTIAL
    "opaque" -> Schema5ConditionCompleteness.OPAQUE
    else -> Schema5ConditionCompleteness.OPAQUE
}

internal fun schema5VisualStatus(raw: String): Schema5VisualStatus = when (raw) {
    "official_own" -> Schema5VisualStatus.OFFICIAL_OWN
    "official_reuse" -> Schema5VisualStatus.OFFICIAL_REUSE
    "official_none" -> Schema5VisualStatus.OFFICIAL_NONE
    "proxy" -> Schema5VisualStatus.PROXY
    "pending_review" -> Schema5VisualStatus.PENDING_REVIEW
    "package_error" -> Schema5VisualStatus.PACKAGE_ERROR
    else -> Schema5VisualStatus.PACKAGE_ERROR
}
