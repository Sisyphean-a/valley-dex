package com.example.stardewoffline.core.json

import com.example.stardewoffline.core.model.DetailPresentation
import com.example.stardewoffline.core.model.EntityDetail

/**
 * 把数据包中的稳定派生字段，以及当前版本中语义明确的官方字段，整理成阅读模型。
 * 未识别的未来字段仍然保留在数据库中，但不会被猜测成用户可见结论。
 */
object DetailPresentationParser {
    fun present(entity: EntityDetail): DetailPresentation {
        val raw = entity.extraJson
        val derived = raw.objectAt("officialDerived")
        val sourceId = entity.id.substringAfter(':', entity.id)
        val facts = DetailFactParser.factsFor(entity.entityType, raw, derived, sourceId)
        val groups = DetailRelationParser.groupsFor(entity.entityType, raw, derived)
        return DetailPresentation(facts, groups.filter { it.relations.isNotEmpty() })
    }
}
