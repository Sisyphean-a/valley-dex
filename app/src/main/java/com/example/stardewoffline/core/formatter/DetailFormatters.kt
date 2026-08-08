package com.example.stardewoffline.core.formatter

import java.math.BigDecimal
import java.util.Locale

/**
 * 详情页只把有明确游戏语义的值转换成中文；无法确认含义的值不擅自猜测。
 */
object DetailFormatters {
    private val seasons = mapOf(
        "spring" to "春季",
        "summer" to "夏季",
        "fall" to "秋季",
        "autumn" to "秋季",
        "winter" to "冬季",
    )

    private val locations = mapOf(
        "beach" to "海滩",
        "town" to "鹈鹕镇",
        "forest" to "煤矿森林",
        "mountain" to "山区",
        "undergroundmine" to "矿井",
        "mine" to "矿井",
        "seedshop" to "皮埃尔杂货店",
        "saloon" to "星之果实餐吧",
        "jojomart" to "Joja超市",
        "marnieranch" to "玛妮牧场",
        "marniehouse" to "玛妮家",
        "sebastianroom" to "塞巴斯蒂安的房间",
        "haleyhouse" to "海莉家",
        "sandyhouse" to "桑迪家",
        "alexhouse" to "亚历克斯家",
        "emilyhouse" to "艾米丽家",
        "samhouse" to "山姆家",
        "jodihouse" to "乔迪家",
        "pennytrailer" to "潘姆的拖车",
        "elliotthouse" to "艾利欧特的小屋",
        "communitycenter" to "社区中心",
        "wizardhouse" to "巫师塔",
        "carpentershop" to "木匠的商店",
        "leahshouse" to "莉亚的家",
        "harveysclinic" to "哈维诊所",
        "museum" to "博物馆",
        "blacksmith" to "铁匠铺",
        "animalshop" to "动物商店",
        "adventurersguild" to "冒险家公会",
        "busstop" to "巴士站",
        "railroad" to "铁路",
        "bathhouse" to "浴场",
        "witchhut" to "女巫小屋",
        "islandtrader" to "姜岛商人",
        "volcano" to "火山地牢",
        "sewer" to "下水道",
        "submarine" to "夜市潜艇",
        "desert" to "沙漠",
        "witchswamp" to "女巫沼泽",
        "farm" to "农场",
        "farmhouse" to "农舍",
        "farmcave" to "农场洞穴",
        "bugland" to "突变虫穴",
        "backwoods" to "偏僻森林",
        "islandwest" to "姜岛西部",
        "islandnorth" to "姜岛北部",
        "islandsouth" to "姜岛南部",
        "islandsoutheast" to "姜岛东南部",
        "islandeast" to "姜岛东部",
        "caldera" to "姜岛火山口",
        "islandwestcave" to "姜岛西部洞穴",
        "islandsoutheastcave" to "姜岛东南部洞穴",
        "islandnorthcave" to "姜岛北部洞穴",
        "islandsouthcave" to "姜岛南部洞穴",
    )

    private val categories = mapOf(
        -999 to "垃圾",
        -101 to "小饰品",
        -100 to "衣服",
        -99 to "工具",
        -98 to "武器",
        -97 to "靴子",
        -96 to "戒指",
        -95 to "帽子",
        -81 to "采集物",
        -80 to "花",
        -79 to "水果",
        -75 to "蔬菜",
        -74 to "种子",
        -29 to "装备",
        -28 to "怪物战利品",
        -27 to "糖浆",
        -26 to "工匠物品",
        -25 to "材料",
        -24 to "家具",
        -23 to "可在鱼店出售",
        -22 to "鱼具",
        -21 to "鱼饵",
        -20 to "垃圾",
        -19 to "肥料",
        -18 to "可在皮埃尔和玛妮处出售",
        -17 to "可在皮埃尔处出售",
        -16 to "建筑资源",
        -15 to "金属资源",
        -14 to "肉类",
        -12 to "矿物",
        -9 to "大型工艺品",
        -8 to "制造材料",
        -7 to "烹饪材料",
        -6 to "牛奶",
        -5 to "蛋",
        -4 to "鱼",
        -2 to "宝石",
    )

    private val contextTags = mapOf(
        "bone_item" to "骨头（任意）",
        "egg_item" to "鸡蛋（任意）",
        "large_egg_item" to "大鸡蛋（任意）",
        "fish_legendary" to "传奇鱼",
        "fish_ocean" to "海鱼",
        "fish_freshwater" to "淡水鱼",
        "fish_lake" to "湖鱼",
        "fish_river" to "河鱼",
        "island" to "姜岛相关",
        "seedmaker_banned" to "不能放入种子机",
        "crystalarium_banned" to "不能放入宝石复制机",
        "dye_medium" to "中等染料强度",
        "dye_red" to "红色染料",
        "dye_orange" to "橙色染料",
        "dye_yellow" to "黄色染料",
        "dye_green" to "绿色染料",
        "dye_blue" to "蓝色染料",
        "dye_purple" to "紫色染料",
        "color_red" to "红色",
        "color_orange" to "橙色",
        "color_yellow" to "黄色",
        "color_green" to "绿色",
        "color_blue" to "蓝色",
        "color_purple" to "紫色",
        "color_prismatic" to "彩虹色",
        "color_brown" to "棕色",
        "color_white" to "白色",
        "color_gray" to "灰色",
        "color_sand" to "沙色",
        "color_gold" to "金色",
        "color_pink" to "粉色",
        "color_cyan" to "青色",
        "color_black" to "黑色",
        "color_iridium" to "铱色",
        "color_copper" to "铜色",
        "color_iron" to "铁色",
        "color_jade" to "翡翠色",
        "color_lime" to "青柠色",
        "color_salmon" to "鲑红色",
        "color_aquamarine" to "海蓝宝色",
    )

    fun season(value: String) = seasons[value.trim().lowercase(Locale.ROOT)] ?: value

    fun seasons(values: List<String>) = values.joinToString("、", transform = ::season)

    fun bool(value: Boolean) = if (value) "是" else "否"

    fun booleanText(value: String): String? = when (value.trim().lowercase(Locale.ROOT)) {
        "true", "1", "yes" -> "是"
        "false", "0", "no" -> "否"
        else -> null
    }

    fun chance(value: Double) = percentage(value)

    fun percentage(value: Double): String {
        return if (value in 0.0..1.0) "${decimal(value * 100)}%" else decimal(value)
    }

    fun percentage(value: String): String? = value.toDoubleOrNull()?.let(::percentage)

    fun gold(value: String): String? = formatNumber(value)?.let { "$it 金" }

    fun gameTime(value: Int) = value.takeIf(::isGameTime)?.let { "%02d:%02d".format(Locale.ROOT, it / 100, it % 100) } ?: value.toString()

    fun gameTimes(values: List<Int>): String? {
        if (values.isEmpty()) return null
        val ranges = values.chunked(2).map { pair ->
            if (pair.size == 1) gameTime(pair[0]) else "${gameTime(pair[0])} - ${gameTime(pair[1])}"
        }
        return ranges.joinToString("、")
    }

    fun durationMinutes(value: Int): String? {
        if (value < 0) return null
        if (value == 0) return "立即完成"
        var remaining = value
        val days = remaining / MINUTES_PER_DAY
        remaining %= MINUTES_PER_DAY
        val hours = remaining / 60
        val minutes = remaining % 60
        return buildList {
            if (days > 0) add("${days}天")
            if (hours > 0) add("${hours}小时")
            if (minutes > 0) add("${minutes}分钟")
        }.joinToString(" ")
    }

    fun days(value: Int): String? = value.takeIf { it >= 0 }?.let { "${it}天" }

    fun quality(value: String): String? = when (value.trim().toIntOrNull()) {
        -1 -> "默认品质"
        0 -> "普通"
        1 -> "银星"
        2 -> "金星"
        4 -> "铱星"
        null -> value.takeIf(String::isNotBlank)
        else -> "未知品质（${value.trim()}）"
    }

    fun currency(value: String): String? = when (value.trim().toIntOrNull()) {
        0 -> "金币"
        1 -> "节日积分"
        2 -> "赌场币"
        4 -> "齐币"
        null -> value.takeIf(String::isNotBlank)
        else -> "未知货币（${value.trim()}）"
    }

    fun category(value: String): String? = value.trim().toIntOrNull()?.let { categories[it] ?: "未知分类（$it）" }

    fun categoryTag(value: String): String? = value.removePrefix("category_").let { name ->
        val category = when (name) {
            "artisan_goods" -> -26
            "bait" -> -21
            "big_craftable" -> -9
            "boots" -> -97
            "clothing" -> -100
            "cooking" -> -7
            "crafting" -> -8
            "egg" -> -5
            "equipment" -> -29
            "fertilizer" -> -19
            "fish" -> -4
            "flowers" -> -80
            "fruits" -> -79
            "furniture" -> -24
            "gem" -> -2
            "greens" -> -81
            "hat" -> -95
            "ingredients" -> -25
            "junk", "litter" -> -20
            "meat" -> -14
            "milk" -> -6
            "minerals" -> -12
            "monster_loot" -> -28
            "ring" -> -96
            "seeds" -> -74
            "sell_at_fish_shop" -> -23
            "syrup" -> -27
            "tackle" -> -22
            "tool" -> -99
            "vegetable" -> -75
            "weapon" -> -98
            "sell_at_pierres" -> -17
            "sell_at_pierres_and_marnies" -> -18
            "metal_resources" -> -15
            "building_resources" -> -16
            "trinket" -> -101
            else -> null
        }
        category?.let { categories[it] }
    }

    fun contextTag(value: String): String {
        val negated = value.startsWith('!')
        val tag = value.removePrefix("!")
        val readable = when {
            contextTags[tag] != null -> contextTags.getValue(tag)
            tag.startsWith("category_", ignoreCase = true) -> categoryTag(tag.lowercase(Locale.ROOT))
            tag.startsWith("season_", ignoreCase = true) -> "${season(tag.substringAfter('_'))}限定"
            tag.startsWith("color_", ignoreCase = true) -> "${humanize(tag.substringAfter('_'))}颜色"
            tag.startsWith("preserve_sheet_index_", ignoreCase = true) -> "由其他物品加工"
            tag.startsWith("item_", ignoreCase = true) -> "物品标签：${humanize(tag.substringAfter('_'))}"
            else -> "特殊标签：${humanize(tag)}"
        } ?: "特殊标签：${humanize(tag)}"
        return if (negated) "不含：$readable" else readable
    }

    fun contextTags(values: List<String>) = values.filter(String::isNotBlank).joinToString("、", transform = ::contextTag)

    fun location(locationId: String, areaId: String? = null): String {
        val locationName = locations[locationId.trim().lowercase(Locale.ROOT)] ?: humanizeLocation(locationId)
        val areaName = areaId?.trim()?.takeIf(String::isNotEmpty)?.let { fishArea(locationId, it) }
        return if (areaName == null) locationName else "$locationName（$areaName）"
    }

    fun fishArea(locationId: String, areaId: String): String? {
        val normalized = areaId.trim().lowercase(Locale.ROOT)
        val known = when (normalized) {
            "east-pier", "eastpier" -> "码头东侧"
            "northmost-bridge", "northmostbridge" -> "最北桥区"
            "island-tip", "islandtip" -> "河流小岛南端"
            "lake" -> "湖泊"
            "river" -> "河流"
            "freshwater" -> "淡水"
            "ocean" -> "海洋"
            "toppond", "top-pond" -> "上方池塘"
            "bottompond", "bottom-pond" -> "下方池塘"
            else -> null
        }
        if (known != null) return known
        val level = normalized.toIntOrNull()
        if (level != null && locationId.lowercase(Locale.ROOT).contains("mine")) return "第 ${level} 层"
        return if (normalized in setOf("default", "main")) null else areaId
    }

    fun weather(value: String): String = when (value.trim().lowercase(Locale.ROOT)) {
        "sunny", "sun" -> "晴天"
        "rainy", "rain" -> "雨天"
        "both", "any", "all" -> "晴雨皆可"
        else -> value
    }

    fun fishBehavior(value: String): String = when (value.trim().lowercase(Locale.ROOT)) {
        "dart" -> "冲刺型"
        "mixed" -> "混合型"
        "floater" -> "漂浮型"
        "sinker" -> "下沉型"
        "smooth" -> "平滑型"
        "tough" -> "困难型"
        else -> value
    }

    fun weaponType(value: String): String = when (value.trim().toIntOrNull()) {
        0 -> "剑"
        1 -> "匕首"
        2 -> "棍棒"
        3 -> "防御剑"
        else -> "未知武器类型（${value.trim()}）"
    }

    fun weaponSpeed(rawSpeed: String, type: String?): String? {
        val speed = rawSpeed.toIntOrNull() ?: return rawSpeed.takeIf(String::isNotBlank)
        val weaponType = type?.toIntOrNull()
        val displayed = (speed - if (weaponType == 2) -8 else 0) / 2
        return if (displayed == speed) signed(displayed) else "${signed(displayed)}（内部修正 ${signed(speed)}）"
    }

    fun signed(value: Int): String = if (value > 0) "+$value" else value.toString()

    fun skill(value: String): String = when (value.trim().lowercase(Locale.ROOT)) {
        "0", "farming" -> "耕种"
        "1", "fishing" -> "钓鱼"
        "2", "foraging" -> "采集"
        "3", "mining" -> "采矿"
        "4", "combat" -> "战斗"
        "5", "luck" -> "幸运"
        else -> value
    }

    fun toolClass(value: String): String = when (value.trim().lowercase(Locale.ROOT)) {
        "axe" -> "斧头"
        "hoe" -> "锄头"
        "pickaxe" -> "镐子"
        "wateringcan" -> "水壶"
        "fishingrod" -> "鱼竿"
        "milk pail", "milkpail" -> "牛奶桶"
        "shears" -> "剪刀"
        "pan" -> "淘盘"
        "generictool" -> "通用工具"
        "lantern" -> "提灯"
        "wand" -> "魔杖"
        else -> value
    }

    fun age(value: String): String = when (value.trim().lowercase(Locale.ROOT)) {
        "adult" -> "成年"
        "teen" -> "青少年"
        "child" -> "儿童"
        else -> value
    }

    fun gender(value: String): String = when (value.trim().lowercase(Locale.ROOT)) {
        "male" -> "男性"
        "female" -> "女性"
        "undefined" -> "未定义"
        else -> value
    }

    fun socialTrait(value: String): String = when (value.trim().lowercase(Locale.ROOT)) {
        "neutral" -> "中性"
        "outgoing" -> "外向"
        "shy" -> "害羞"
        "polite" -> "礼貌"
        "rude" -> "粗鲁"
        "positive" -> "乐观"
        "negative" -> "悲观"
        else -> value
    }

    fun harvestMethod(value: String): String = when (value.trim().lowercase(Locale.ROOT)) {
        "grab" -> "采摘"
        "scythe" -> "镰刀收割"
        "cut" -> "切割"
        else -> value
    }

    fun outputMethod(value: String): String = when {
        value.contains("OutputSeedMaker", ignoreCase = true) -> "制种机规则"
        value.contains("DROP_IN", ignoreCase = true) -> "按投入物品决定"
        value.isBlank() -> "默认产出"
        else -> "特殊产出规则"
    }

    fun usageType(value: String): String = when (value.trim().lowercase(Locale.ROOT)) {
        "bundle" -> "收集包"
        "cooking_recipe" -> "烹饪配方"
        "crafting_recipe" -> "制作配方"
        "tailoring_recipe" -> "裁缝配方"
        "quest" -> "任务"
        else -> value
    }

    fun condition(value: String?): String? {
        val raw = value?.trim()?.takeIf(String::isNotEmpty) ?: return null
        return raw.split(',').mapNotNull { conditionPart(it.trim()) }.distinct().joinToString("；")
            .ifBlank { "受游戏条件限制（需在游戏中判断）" }
    }

    fun conditionPart(raw: String): String? {
        if (raw.isBlank()) return null
        val negated = raw.startsWith('!')
        val query = raw.removePrefix("!").trim()
        val words = query.split(Regex("\\s+"))
        val result = when (words.firstOrNull()?.uppercase(Locale.ROOT)) {
            "SEASON" -> "季节：${words.drop(1).joinToString("、") { season(it) }}"
            "DAY_OF_MONTH" -> "日期：${words.drop(1).joinToString("、") { if (it.equals("even", true)) "偶数日" else if (it.equals("odd", true)) "奇数日" else "第${it}天" }}"
            "DAY_OF_WEEK" -> "星期：${words.drop(1).joinToString("、", transform = ::weekday)}"
            "PLAYER_HAS_MAIL" -> "已收到相关邮件"
            "PLAYER_HAS_SEEN_EVENT" -> "已触发相关事件"
            "PLAYER_SPECIAL_ORDER_RULE_ACTIVE" -> "相关特别订单进行中"
            "PLAYER_HAS_ACHIEVEMENT" -> "已获得相关成就"
            "PLAYER_HAS_ITEM" -> "拥有指定物品"
            "ITEM_CONTEXT_TAG", "ITEM_EDIBILITY" -> "物品满足指定条件"
            "PLAYER_IS" -> "玩家状态满足要求"
            else -> null
        } ?: return null
        return if (negated) "不满足：$result" else result
    }

    fun specialIngredient(value: String): String? = when (value.trim()) {
        "-1" -> "任意采集物"
        "-2" -> "任意矿物"
        "-3" -> "任意作物"
        "-4" -> "任意鱼"
        "-5" -> "任意蛋"
        "-6" -> "任意牛奶"
        "-777" -> "任意野生种子"
        else -> null
    }

    fun edibility(value: String): String? {
        val number = value.toIntOrNull() ?: return value.takeIf(String::isNotBlank)
        return if (number < 0) "不可食用" else "可食用（食用值 $number）"
    }

    fun integer(value: String): String? = value.trim().toIntOrNull()?.toString() ?: value.takeIf(String::isNotBlank)

    fun decimal(value: Double): String {
        if (!value.isFinite()) return value.toString()
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
    }

    fun formatNumber(value: String): String? = value.trim().toDoubleOrNull()?.let(::decimal) ?: value.takeIf(String::isNotBlank)

    fun conditionOrValue(value: String): String = booleanText(value) ?: condition(value) ?: value

    private fun weekday(value: String): String = when (value.lowercase(Locale.ROOT)) {
        "monday" -> "周一"
        "tuesday" -> "周二"
        "wednesday" -> "周三"
        "thursday" -> "周四"
        "friday" -> "周五"
        "saturday" -> "周六"
        "sunday" -> "周日"
        else -> value
    }

    private fun humanizeLocation(value: String): String = humanize(value)

    private fun humanize(value: String): String = value
        .replace(Regex("(?<=[a-z])(?=[A-Z])"), " ")
        .replace('_', ' ')
        .replace('-', ' ')
        .trim()
        .replaceFirstChar { it.titlecase(Locale.ROOT) }

    private fun isGameTime(value: Int) = value >= 0 && value % 100 in 0..59

    private const val MINUTES_PER_DAY = 24 * 60
}
