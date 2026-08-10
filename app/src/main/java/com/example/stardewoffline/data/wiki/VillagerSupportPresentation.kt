package com.example.stardewoffline.data.wiki

import com.example.stardewoffline.core.formatter.DetailFormatters
import com.example.stardewoffline.core.model.DetailFact
import com.example.stardewoffline.core.model.EntityDetail
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Flow: retain support records in the package, select only records belonging to the villager,
 * then expose parsed schedule facts and gift targets to the catalogue presentation layer.
 */
data class VillagerSupportPresentation(
    val schedules: List<VillagerScheduleItem>,
    val gifts: Map<String, List<VillagerGiftItem>>,
) {
    val giftItemIds: List<String> = gifts.values.flatten().mapNotNull { it.itemId }.distinct()
}

data class VillagerScheduleItem(
    val group: String,
    val order: Int,
    val label: String,
    val details: List<DetailFact>,
)

data class VillagerGiftItem(
    val itemId: String?,
    val readableLabel: String?,
    val details: List<DetailFact>,
)

object VillagerSupportPresentationBuilder {
    private val giftLabels = listOf("最爱", "喜欢", "一般", "不喜欢", "讨厌")
    private val giftIndexes = listOf(1, 3, 5, 7, 9)
    private val scheduleGroupOrder = listOf("春季", "夏季", "秋季", "冬季", "通用日期", "天气与节日", "婚后日程", "其他特殊日程")
        .withIndex().associate { it.value to it.index }

    fun build(sourceId: String, schedules: List<EntityDetail>, gifts: List<EntityDetail>): VillagerSupportPresentation {
        val canonical = canonical(sourceId)
        val matchingSchedules = schedules.filter { matches(canonical, it, "npc_schedule") && !isTemplate(it) }
        val matchingGifts = gifts.filter { matches(canonical, it, "villager_gift") && !isTemplate(it) }
        return VillagerSupportPresentation(
            schedules = matchingSchedules.mapNotNull(::scheduleItem).sortedWith(
                compareBy<VillagerScheduleItem> { scheduleGroupOrder[it.group] ?: Int.MAX_VALUE }.thenBy { it.order },
            ),
            gifts = giftLabels.associateWith { label ->
                val index = giftLabels.indexOf(label)
                matchingGifts.flatMap { giftItems(it, index) }
            },
        )
    }

    private fun matches(source: String, entity: EntityDetail, type: String): Boolean {
        if (entity.entityType != type) return false
        val idSource = entity.id.substringAfter(':', "").split(':', '/', '|').firstOrNull().orEmpty()
        val fileSource = entity.sourceFile?.substringAfterLast('/')?.substringBeforeLast('.')
        val giftSource = entity.extraJson.legacyFields().firstOrNull()
        return listOf(idSource, fileSource, giftSource).any { canonical(it.orEmpty()) == source }
    }

    private fun isTemplate(entity: EntityDetail): Boolean {
        val idSource = entity.id.substringAfter(':', "").split(':', '/', '|').firstOrNull().orEmpty()
        return listOf(idSource, entity.sourceFile.orEmpty(), entity.extraJson.legacyFields().firstOrNull().orEmpty())
            .any { it.substringAfterLast('/').substringBeforeLast('.').equals("template", ignoreCase = true) }
    }

    private fun scheduleItem(entity: EntityDetail): VillagerScheduleItem? {
        val fields = entity.extraJson.legacyFields()
        val context = ScheduleKeyContext.parse(scheduleKey(entity))
        val entryFacts = fields.filter(::isScheduleEntry).flatMap { value ->
            val tokens = value.split(Regex("\\s+")).filter(String::isNotBlank)
            val time = scheduleTime(tokens.firstOrNull()) ?: return@flatMap emptyList()
            val location = tokens.getOrNull(1) ?: return@flatMap emptyList()
            listOf(
                DetailFact("时间", DetailFormatters.gameTime(time)),
                DetailFact("地点", DetailFormatters.location(location)),
            )
        }
        if (entryFacts.isEmpty()) return null
        return VillagerScheduleItem(context.group, context.order, context.label, entryFacts)
    }

    private fun giftItems(entity: EntityDetail, groupIndex: Int): List<VillagerGiftItem> {
        val values = entity.extraJson.legacyFields().getOrNull(giftIndexes[groupIndex])
            ?.split(Regex("\\s+"))?.filter(String::isNotBlank).orEmpty()
        return values.mapNotNull { raw ->
            val readable = DetailFormatters.specialIngredient(raw) ?: DetailFormatters.categoryTag(raw)
            when {
                readable != null -> VillagerGiftItem(null, readable, listOf(DetailFact("范围", readable)))
                isItemReference(raw) -> VillagerGiftItem(raw, null, emptyList())
                else -> null
            }
        }
    }

    private fun scheduleKey(entity: EntityDetail): String = entity.id.split(':', limit = 3).getOrNull(2).orEmpty()

    private fun canonical(value: String): String = when (value.trim().lowercase()) {
        "leomainland" -> "leo"
        else -> value.trim().lowercase()
    }

    private fun isScheduleEntry(value: String): Boolean = scheduleTime(value.split(Regex("\\s+")).firstOrNull()) != null
    private fun scheduleTime(value: String?): Int? = value?.removePrefix("a")?.toIntOrNull()
    private fun isItemReference(value: String): Boolean =
        !value.contains("FLAVORED_ITEM", true) && !value.contains("DROP_IN", true) &&
            !value.contains("NEARBY_FLOWER", true) && value.matches(Regex("(?:\\([A-Z]+\\))?[-A-Za-z0-9_:.]+"))

    private fun JsonObject.legacyFields(): List<String> {
        val values = (this["legacyFields"] as? JsonArray)
            ?.map { (it as? JsonPrimitive)?.contentOrNull.orEmpty() }
            .orEmpty()
        if (values.isNotEmpty()) return values
        return ((this["legacyValue"] as? JsonPrimitive)?.contentOrNull ?: "").split('/').toList()
    }

    private data class ScheduleKeyContext(
        val group: String,
        val order: Int,
        val label: String,
        val description: String,
    ) {
        companion object {
            private val seasons = mapOf(
                "spring" to "春季",
                "summer" to "夏季",
                "fall" to "秋季",
                "winter" to "冬季",
            )
            private val weekdays = mapOf(
                "mon" to "周一",
                "tue" to "周二",
                "wed" to "周三",
                "thu" to "周四",
                "fri" to "周五",
                "sat" to "周六",
                "sun" to "周日",
            )

            fun parse(rawKey: String): ScheduleKeyContext {
                val key = rawKey.trim()
                val parts = key.split('_').filter(String::isNotBlank)
                val lowerParts = parts.map(String::lowercase)
                val season = seasons[lowerParts.firstOrNull()]
                val day = lowerParts.getOrNull(1)?.toIntOrNull()
                val weekday = weekdays[lowerParts.getOrNull(1)]
                val friendshipTier = lowerParts.getOrNull(2)?.toIntOrNull()
                if (season != null) {
                    return when {
                        day != null && friendshipTier != null -> ScheduleKeyContext(season, 10 + day, "第${day}天（友谊等级${friendshipTier}）", "${season}第${day}天，友谊等级条件 ${friendshipTier}")
                        day != null -> ScheduleKeyContext(season, 10 + day, "第${day}天", "${season}第${day}天")
                        weekday != null && friendshipTier != null -> ScheduleKeyContext(season, 100 + friendshipTier, "$weekday（友谊等级${friendshipTier}）", "${season}${weekday}，友谊等级条件 ${friendshipTier}")
                        weekday != null -> ScheduleKeyContext(season, 100, weekday, "${season}${weekday}")
                        parts.size > 1 -> ScheduleKeyContext(season, 1000, "特殊日程", "${season}特殊规则")
                        else -> ScheduleKeyContext(season, 0, "默认日程", "${season}默认日程")
                    }
                }
                if (key.matches(Regex("\\d+(?:_\\d+)?"))) {
                    val genericDay = parts.first()
                    val genericFriendshipTier = parts.getOrNull(1)?.toIntOrNull()
                    val label = genericFriendshipTier?.let { "第${genericDay}天（友谊等级${it}）" } ?: "第${genericDay}天"
                    val description = genericFriendshipTier?.let { "不区分季节的第${genericDay}天，友谊等级条件 $it" } ?: "不区分季节的第${genericDay}天日程"
                    return ScheduleKeyContext("通用日期", 10 + genericDay.toInt(), label, description)
                }
                val genericWeekday = weekdays[lowerParts.firstOrNull()]
                if (genericWeekday != null) {
                    val suffix = lowerParts.getOrNull(1)
                    val friendshipTier = suffix?.toIntOrNull()
                    return when {
                        suffix.equals("normal", true) -> ScheduleKeyContext("通用日期", 100, "${genericWeekday}常规日程", "不区分季节的${genericWeekday}常规日程")
                        friendshipTier != null -> ScheduleKeyContext("通用日期", 100 + friendshipTier, "${genericWeekday}（友谊等级${friendshipTier}）", "不区分季节的${genericWeekday}，友谊等级条件 ${friendshipTier}")
                        suffix == null -> ScheduleKeyContext("通用日期", 100, genericWeekday, "不区分季节的${genericWeekday}日程")
                        else -> ScheduleKeyContext("通用日期", 110, "${genericWeekday}特殊日程", "不区分季节的${genericWeekday}特殊规则")
                    }
                }
                if (key.equals("marriageJob", true)) return ScheduleKeyContext("婚后日程", 0, "婚后工作日程", "婚后工作日程")
                if (key.startsWith("marriage_", ignoreCase = true)) {
                    val suffix = key.substringAfter('_')
                    val nested = parse(suffix)
                    val label = if (nested.group in seasons.values) "婚后·${nested.group}${nested.label}" else "婚后·${readableSpecial(suffix)}"
                    return ScheduleKeyContext("婚后日程", 0, label, "婚后专用日程：${readableSpecial(suffix)}")
                }
                if (key.equals("rain", true)) return ScheduleKeyContext("天气与节日", 0, "下雨天", "下雨时使用")
                if (key.equals("rain2", true)) return ScheduleKeyContext("天气与节日", 1, "下雨天（变体）", "下雨时的另一套日程")
                if (key.equals("greenrain", true)) return ScheduleKeyContext("天气与节日", 2, "绿雨天", "绿雨天气使用")
                if (key.isBlank()) return ScheduleKeyContext("其他特殊日程", 0, "日程规则", "数据未提供具体季节或日期")
                return ScheduleKeyContext("天气与节日", 10, readableSpecial(key), "特殊日程规则：${readableSpecial(key)}")
            }

            private fun readableSpecial(value: String): String = when {
                value.startsWith("DesertFestival", true) -> "沙漠节${value.substringAfter('_', "").takeIf(String::isNotBlank)?.let { "第${it}天" }.orEmpty()}"
                value.startsWith("marriage", true) -> "婚后特殊日程"
                value.equals("troutderby", true) -> "鳟鱼大赛"
                value.equals("squidfest", true) -> "鱿鱼节"
                value.equals("communitycenter_replacement", true) -> "社区中心替换规则"
                value.equals("jojomart_replacement", true) -> "Joja超市替换规则"
                value.equals("default", true) -> "默认规则"
                else -> "特殊日程"
            }
        }
    }
}
