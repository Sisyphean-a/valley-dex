package com.example.stardewoffline.core.datapackage

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.stardewoffline.core.common.AppResult
import com.example.stardewoffline.core.common.getOrNull
import com.example.stardewoffline.core.model.CatalogueQuery
import com.example.stardewoffline.core.model.EntryFact
import com.example.stardewoffline.core.model.WikiEntry
import com.example.stardewoffline.core.model.WikiEntrySubmenu
import com.example.stardewoffline.data.wiki.Schema5WikiCatalogue
import com.example.stardewoffline.testsupport.TestAppScenario
import com.example.stardewoffline.testsupport.instrumentationTestContext
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * R1 红色产品门禁（App 侧）：真实 schema 5 包经过 Schema5WikiCatalogue 投影后，
 * 玩家界面必须零技术泄露。当前实现会失败——失败信息指出实体与泄露值。
 *
 * 与 [RealV5PackageAcceptanceTest] 相同，通过 instrumentation 参数注入真实包：
 * `-e realV5Required true -e realV5PackagePath <path>`
 */
@RunWith(AndroidJUnit4::class)
class RealV5PlayerGateTest {
    @Test
    fun everyBrowsableCategoryTitleIsApprovedChinese() = runBlocking {
        val catalogue = installedCatalogue()
        val sections = catalogue.sections().getOrNull() ?: error("真实数据包没有可读分类")
        val violations = sections.flatMap { section -> section.categories }
            .filter { category -> !APPROVED_CATEGORY_TITLE.matches(category.title) }
            .map { category -> "分类标题 ${category.id} -> ${category.title}" }
        assertTrue("存在未批准中文分类名：${violations.joinToString("；")}", violations.isEmpty())
    }

    @Test
    fun jodiEntryHasNoTechnicalLeaksAndLocalizedCoreAnswers() = runBlocking {
        val catalogue = installedCatalogue()
        val entry = catalogue.entry("villager:Jodi").getOrNull() ?: error("真实包缺少 villager:Jodi")
        val violations = mutableListOf<String>()
        if (!APPROVED_CATEGORY_TITLE.matches(entry.categoryLabel)) {
            violations += "乔迪条目类别标签泄露内部类型名：${entry.categoryLabel}"
        }
        val birthday = entry.sections.flatMap { it.facts }.firstOrNull { it.label == "生日" }
        if (birthday == null) {
            violations += "乔迪缺少生日核心答案"
        } else if (birthday.value != "秋季 11 日") {
            violations += "乔迪生日未本地化：${birthday.value}，应为「秋季 11 日」"
        }

        val immediateFacts = entry.sections.firstOrNull { it.title == "立即行动" }?.facts.orEmpty()
        val allFacts = entry.sections.flatMap { it.facts }
        if (allFacts.any { it.label == "礼物偏好" }) {
            violations += "乔迪 混入应折叠的完整资料（礼物偏好应进入可展开资料）"
        }
        if (allFacts.count { it.label == "日程" } > 1) {
            violations += "乔迪 日程明细混入普通事实（完整日程应进入可展开资料）"
        }
        val immediateLabels = immediateFacts.map { it.label }
        if ("常住地" in immediateLabels && "生日" in immediateLabels &&
            immediateLabels.indexOf("常住地") > immediateLabels.indexOf("生日")
        ) {
            violations += "乔迪 / 立即行动 顺序违反人物契约（常住地应排在生日之前）"
        }
        for (section in entry.sections) {
            for (fact in section.facts) {
                violations += leaksOf("乔迪 / ${section.title} / ${fact.label}", fact.value)
                if ("来源：" in fact.value) {
                    violations += "乔迪 / ${section.title} / ${fact.label} 把来源重复拼进玩家事实：${fact.value}"
                }
            }
        }
        val sourceSection = entry.sections.firstOrNull { it.title == "数据说明" }
        if (sourceSection != null) {
            for (fact in sourceSection.facts) {
                if (!APPROVED_SOURCE_PHRASING.containsMatchIn(fact.value)) {
                    violations += "乔迪 / 数据说明 使用非玩家文案：${fact.value}"
                }
            }
        }
        entry.submenus.forEach { submenu -> violations += submenuLeaks("乔迪", submenu) }
        for (relation in entry.relations) {
            if (!APPROVED_CATEGORY_TITLE.matches(relation.section)) {
                violations += "乔迪 / 关系分组 泄露内部名：${relation.section}"
            }
            violations += leaksOf("乔迪 / 关系 / ${relation.label}", relation.label)
            for (detail in relation.details) {
                violations += leaksOf("乔迪 / 关系 / ${detail.label}", detail.value)
            }
        }
        assertTrue("乔迪条目存在玩家界面泄露：${violations.take(12).joinToString("；")}", violations.isEmpty())
    }

    @Test
    fun everyVillagerCardHasActionSummaries() = runBlocking {
        val catalogue = installedCatalogue()
        val page = catalogue.entries(CatalogueQuery("type:villager")).getOrNull()
            ?: error("村民分类无法读取")
        val missing = page.entries.filter { it.actionSummary1 == null || it.actionSummary2 == null }
            .map { it.title }
        assertTrue("村民卡片缺少行动摘要：${missing.take(12).joinToString("、")}", missing.isEmpty())
    }

    @Test
    fun noPlayerFactValueIsARawEntityReference() = runBlocking {
        val catalogue = installedCatalogue()
        val page = catalogue.entries(CatalogueQuery("type:cooking_recipe", pageSize = 5)).getOrNull()
            ?: error("料理分类无法读取")
        val sample = page.entries.first()
        val entry = catalogue.entry(sample.id).getOrNull() ?: error("条目无法读取：${sample.id}")
        val violations = entry.sections.flatMap { section ->
            section.facts.filter { fact -> hasRawReferenceSegment(fact.value) }
                .map { fact -> "${entry.id} / ${section.title} / ${fact.label} 原始实体引用：${fact.value}" }
        }
        assertTrue("存在原始实体引用进入玩家事实：${violations.take(8).joinToString("；")}", violations.isEmpty())
    }

    @Test
    fun vincentAndJasShowOnlyUnspecifiedFamilyAssociation() = runBlocking {
        val catalogue = installedCatalogue()
        val vincent = catalogue.entry("villager:Vincent").getOrNull() ?: error("真实包缺少 villager:Vincent")
        val jas = catalogue.entry("villager:Jas").getOrNull() ?: error("真实包缺少 villager:Jas")
        for ((who, entry, other) in listOf(
            Triple("文森特", vincent, "贾斯"),
            Triple("贾斯", jas, "文森特"),
        )) {
            val relation = entry.relations.firstOrNull { it.target.displayNameOrNull() == other }
            assertNotNull("$who 页面缺少与$other 的关联", relation)
            assertTrue(
                "$who → $other 未按决策 05 显示亲友关联：${relation?.label}",
                relation?.label == "亲友关联（具体关系未注明）",
            )
        }
        val romanticRows = listOf(vincent.relations, jas.relations).flatten().filter {
            it.label.contains("恋爱") || it.label.contains("角色资料关联") ||
                it.details.any { detail -> detail.value.contains("恋爱") }
        }
        assertTrue(
            "不可婚配的文森特/贾斯不应出现恋爱语义行：$romanticRows",
            romanticRows.isEmpty(),
        )
    }

    @Test
    fun parsnipEntryFollowsCropContract() = runBlocking {
        val catalogue = installedCatalogue()
        val entry = catalogue.entry("crop:24").getOrNull() ?: error("真实包缺少 crop:24")
        val violations = mutableListOf<String>()
        if (!APPROVED_CATEGORY_TITLE.matches(entry.categoryLabel)) {
            violations += "防风草类别标签泄露内部类型名：${entry.categoryLabel}"
        }
        val immediate = entry.sections.firstOrNull { it.title == "立即行动" }?.facts.orEmpty()
        val labels = immediate.map { it.label }
        if (labels != listOf("季节", "成熟", "种子", "收获物", "出售价格")) {
            violations += "防风草立即行动顺序违反作物契约：$labels"
        }
        val season = immediate.firstOrNull { it.label == "季节" }?.value
        if (season != "春季") violations += "防风草季节未本地化：$season"
        val price = immediate.firstOrNull { it.label == "出售价格" }?.value
        if (price != "35 金币") violations += "防风草出售价格错误：$price"
        val seed = immediate.firstOrNull { it.label == "种子" }?.value.orEmpty()
        if (!seed.startsWith("防风草种子")) violations += "防风草种子未解析为中文：$seed"
        for (section in entry.sections) {
            for (fact in section.facts) violations += leaksOf("防风草 / ${section.title} / ${fact.label}", fact.value)
        }
        val uses = entry.submenus.firstOrNull { it.title == "用途" }
        if (uses == null) violations += "防风草缺少可展开用途资料"
        entry.submenus.forEach { submenu -> violations += submenuLeaks("防风草", submenu) }
        assertTrue("防风草条目存在玩家界面泄露：${violations.take(10).joinToString("；")}", violations.isEmpty())
    }

    @Test
    fun pierreShopFollowsShopContract() = runBlocking {
        val catalogue = installedCatalogue()
        val entry = catalogue.entry("shop:SeedShop").getOrNull() ?: error("真实包缺少 shop:SeedShop")
        val violations = mutableListOf<String>()
        if (!APPROVED_CATEGORY_TITLE.matches(entry.categoryLabel)) {
            violations += "商店类别标签泄露内部类型名：${entry.categoryLabel}"
        }
        val immediate = entry.sections.firstOrNull { it.title == "立即行动" }?.facts.orEmpty()
        val labels = immediate.map { it.label }
        if (labels.take(4) != listOf("商店类型", "地点", "营业时间", "店主")) {
            violations += "商店立即行动顺序违反契约：$labels"
        }
        val kind = immediate.firstOrNull { it.label == "商店类型" }?.value
        if (kind != "普通商店") violations += "商店类型错误：$kind"
        val location = immediate.firstOrNull { it.label == "地点" }?.value
        if (location != "皮埃尔杂货店") violations += "商店地点错误：$location"
        val hours = immediate.firstOrNull { it.label == "营业时间" }?.value.orEmpty()
        if (!hours.startsWith("随店主日程变化")) violations += "商店营业规则错误：$hours"
        val offers = entry.submenus.firstOrNull { it.title == "商品" }
        if (offers == null || (offers.groups.firstOrNull()?.items?.size ?: 0) < 3) {
            violations += "商店缺少完整商品报价资料"
        }
        for (section in entry.sections) {
            for (fact in section.facts) {
                violations += leaksOf("皮埃尔商店 / ${section.title} / ${fact.label}", fact.value)
            }
        }
        entry.submenus.forEach { submenu -> violations += submenuLeaks("皮埃尔商店", submenu) }
        assertTrue("皮埃尔商店存在玩家界面泄露：${violations.take(10).joinToString("；")}", violations.isEmpty())
    }

    @Test
    fun copperPickaxeFollowsToolContract() = runBlocking {
        val catalogue = installedCatalogue()
        val entry = catalogue.entry("tool:CopperPickaxe").getOrNull() ?: error("真实包缺少 tool:CopperPickaxe")
        val violations = mutableListOf<String>()
        if (!APPROVED_CATEGORY_TITLE.matches(entry.categoryLabel)) {
            violations += "工具类别标签泄露内部类型名：${entry.categoryLabel}"
        }
        val immediate = entry.sections.firstOrNull { it.title == "立即行动" }?.facts.orEmpty()
        val labels = immediate.map { it.label }
        if (labels.take(2) != listOf("类型", "档位")) {
            violations += "工具立即行动顺序违反契约：$labels"
        }
        if (immediate.firstOrNull { it.label == "类型" }?.value != "十字镐") {
            violations += "工具类型错误：${immediate.firstOrNull { it.label == "类型" }?.value}"
        }
        if (immediate.firstOrNull { it.label == "档位" }?.value != "铜") {
            violations += "工具档位错误：${immediate.firstOrNull { it.label == "档位" }?.value}"
        }
        val upgradeLabels = labels.filter { it.startsWith("升级") }
        if (upgradeLabels != listOf("升级材料", "升级价格", "升级地点", "升级耗时")) {
            violations += "工具升级信息不完整：$upgradeLabels"
        }
        for (section in entry.sections) {
            for (fact in section.facts) violations += leaksOf("铜十字镐 / ${section.title} / ${fact.label}", fact.value)
        }
        entry.submenus.forEach { submenu -> violations += submenuLeaks("铜十字镐", submenu) }
        assertTrue("铜十字镐条目存在玩家界面泄露：${violations.take(10).joinToString("；")}", violations.isEmpty())
    }

    @Test
    fun beeHouseFollowsBigCraftableContract() = runBlocking {
        val catalogue = installedCatalogue()
        val entry = catalogue.entry("big_craftable:10").getOrNull() ?: error("真实包缺少 big_craftable:10")
        val violations = mutableListOf<String>()
        if (!APPROVED_CATEGORY_TITLE.matches(entry.categoryLabel)) {
            violations += "大型可制作物类别标签泄露内部类型名：${entry.categoryLabel}"
        }
        val immediate = entry.sections.firstOrNull { it.title == "立即行动" }?.facts.orEmpty()
        val product = immediate.firstOrNull { it.label == "主要产物" }?.value
        if (product != "蜂蜜") violations += "蜂房主要产物错误：$product"
        val unlock = immediate.firstOrNull { it.label == "解锁" }?.value
        if (unlock != "耕种等级 3") violations += "蜂房解锁错误：$unlock"
        val materials = entry.submenus.firstOrNull { it.title == "材料清单" }
        if (materials == null || (materials.groups.firstOrNull()?.items?.size ?: 0) != 4) {
            violations += "蜂房缺少完整材料清单（应为 4 种）"
        }
        for (section in entry.sections) {
            for (fact in section.facts) violations += leaksOf("蜂房 / ${section.title} / ${fact.label}", fact.value)
        }
        entry.submenus.forEach { submenu -> violations += submenuLeaks("蜂房", submenu) }
        assertTrue("蜂房条目存在玩家界面泄露：${violations.take(10).joinToString("；")}", violations.isEmpty())
    }

    @Test
    fun pufferfishFollowsFishContract() = runBlocking {
        val catalogue = installedCatalogue()
        val entry = catalogue.entry("fish:128").getOrNull() ?: error("真实包缺少 fish:128")
        val violations = mutableListOf<String>()
        if (!APPROVED_CATEGORY_TITLE.matches(entry.categoryLabel)) {
            violations += "河豚类别标签泄露内部类型名：${entry.categoryLabel}"
        }
        val immediate = entry.sections.firstOrNull { it.title == "立即行动" }?.facts.orEmpty()
        val labels = immediate.map { it.label }
        if (labels != listOf("捕捞地点", "季节", "捕捞时间", "天气", "难度")) {
            violations += "河豚立即行动顺序违反鱼类契约：$labels"
        }
        val location = immediate.firstOrNull { it.label == "捕捞地点" }?.value.orEmpty()
        if (!location.startsWith("海滩")) violations += "河豚捕捞地点未本地化：$location"
        val season = immediate.firstOrNull { it.label == "季节" }?.value
        if (season != "夏季") violations += "河豚季节错误：$season"
        val time = immediate.firstOrNull { it.label == "捕捞时间" }?.value
        if (time != "12:00–16:00") violations += "河豚捕捞时间未本地化：$time"
        val weather = immediate.firstOrNull { it.label == "天气" }?.value
        if (weather != "晴天") violations += "河豚天气未本地化：$weather"
        val behavior = entry.sections.flatMap { it.facts }.firstOrNull { it.label == "行为" }?.value
        if (behavior != "漂浮型") violations += "河豚行为未本地化：$behavior"
        val details = entry.sections.firstOrNull { it.title == "捕捞地点详情" }
        if (details == null || details.facts.isEmpty()) violations += "河豚缺少逐地点捕捞详情"
        for (section in entry.sections) {
            for (fact in section.facts) violations += leaksOf("河豚 / ${section.title} / ${fact.label}", fact.value)
        }
        assertTrue("河豚条目存在玩家界面泄露：${violations.take(10).joinToString("；")}", violations.isEmpty())
    }

    @Test
    fun greenSlimeFollowsMonsterContract() = runBlocking {
        val catalogue = installedCatalogue()
        val entry = catalogue.entry("monster:Green-Slime").getOrNull() ?: error("真实包缺少 monster:Green-Slime")
        val violations = mutableListOf<String>()
        if (!APPROVED_CATEGORY_TITLE.matches(entry.categoryLabel)) {
            violations += "怪物类别标签泄露内部类型名：${entry.categoryLabel}"
        }
        val immediate = entry.sections.firstOrNull { it.title == "立即行动" }?.facts.orEmpty()
        val labels = immediate.map { it.label }
        if (labels.take(5) != listOf("出现地点", "出现楼层", "生命值", "伤害", "掉落")) {
            violations += "绿色史莱姆立即行动顺序违反怪物契约：$labels"
        }
        if (immediate.firstOrNull { it.label == "出现地点" }?.value != "矿井") {
            violations += "绿色史莱姆出现地点错误：${immediate.firstOrNull { it.label == "出现地点" }?.value}"
        }
        if (immediate.firstOrNull { it.label == "出现楼层" }?.value != "矿井 1-39 层") {
            violations += "绿色史莱姆出现楼层错误：${immediate.firstOrNull { it.label == "出现楼层" }?.value}"
        }
        if (immediate.firstOrNull { it.label == "生命值" }?.value != "24") {
            violations += "绿色史莱姆生命值错误：${immediate.firstOrNull { it.label == "生命值" }?.value}"
        }
        if (immediate.firstOrNull { it.label == "伤害" }?.value != "5") {
            violations += "绿色史莱姆伤害错误：${immediate.firstOrNull { it.label == "伤害" }?.value}"
        }
        val drops = immediate.firstOrNull { it.label == "掉落" }?.value.orEmpty()
        if (!drops.startsWith("史莱姆泥")) violations += "绿色史莱姆掉落未解析为中文：$drops"
        for (section in entry.sections) {
            for (fact in section.facts) violations += leaksOf("绿色史莱姆 / ${section.title} / ${fact.label}", fact.value)
        }
        assertTrue("绿色史莱姆条目存在玩家界面泄露：${violations.take(10).joinToString("；")}", violations.isEmpty())
    }

    @Test
    fun rustySwordFollowsWeaponContract() = runBlocking {
        val catalogue = installedCatalogue()
        val entry = catalogue.entry("weapon:0").getOrNull() ?: error("真实包缺少 weapon:0")
        val violations = mutableListOf<String>()
        if (!APPROVED_CATEGORY_TITLE.matches(entry.categoryLabel)) {
            violations += "武器类别标签泄露内部类型名：${entry.categoryLabel}"
        }
        val immediate = entry.sections.firstOrNull { it.title == "立即行动" }?.facts.orEmpty()
        val labels = immediate.map { it.label }
        if (labels.take(3) != listOf("武器类型", "伤害", "获得方式")) {
            violations += "生锈的剑立即行动顺序违反武器契约：$labels"
        }
        if (immediate.firstOrNull { it.label == "武器类型" }?.value != "剑") {
            violations += "生锈的剑武器类型未本地化：${immediate.firstOrNull { it.label == "武器类型" }?.value}"
        }
        if (immediate.firstOrNull { it.label == "伤害" }?.value != "2–5") {
            violations += "生锈的剑伤害区间错误：${immediate.firstOrNull { it.label == "伤害" }?.value}"
        }
        val acquisition = immediate.firstOrNull { it.label == "获得方式" }?.value.orEmpty()
        if (!acquisition.startsWith("商店购买")) violations += "生锈的剑获得方式错误：$acquisition"
        for (section in entry.sections) {
            for (fact in section.facts) violations += leaksOf("生锈的剑 / ${section.title} / ${fact.label}", fact.value)
        }
        assertTrue("生锈的剑条目存在玩家界面泄露：${violations.take(10).joinToString("；")}", violations.isEmpty())
    }

    @Test
    fun legendaryFishNamesAreChinese() = runBlocking {
        val catalogue = installedCatalogue()
        val angler = catalogue.entry("fish:899").getOrNull() ?: error("真实包缺少 fish:899")
        val glacier = catalogue.entry("fish:902").getOrNull() ?: error("真实包缺少 fish:902")
        assertTrue("雌鮟鱇鱼标题未本地化：${angler.title}", angler.title == "雌鮟鱇鱼")
        assertTrue("小冰川鱼标题未本地化：${glacier.title}", glacier.title == "小冰川鱼")
        val iridiumGolem = catalogue.entry("monster:Iridium-Golem").getOrNull()
            ?: error("真实包缺少 monster:Iridium-Golem")
        val truffleCrab = catalogue.entry("monster:Truffle-Crab").getOrNull()
            ?: error("真实包缺少 monster:Truffle-Crab")
        assertTrue("铱石魔标题未本地化：${iridiumGolem.title}", iridiumGolem.title == "铱石魔")
        assertTrue("松露蟹标题未本地化：${truffleCrab.title}", truffleCrab.title == "松露蟹")
    }

    @Test
    fun copperOreFollowsItemContract() = runBlocking {
        val catalogue = installedCatalogue()
        val entry = catalogue.entry("object:378").getOrNull() ?: error("真实包缺少 object:378")
        val violations = mutableListOf<String>()
        if (!APPROVED_CATEGORY_TITLE.matches(entry.categoryLabel)) {
            violations += "物品类别标签泄露内部类型名：${entry.categoryLabel}"
        }
        val immediate = entry.sections.firstOrNull { it.title == "立即行动" }?.facts.orEmpty()
        val labels = immediate.map { it.label }
        if (labels.take(3) != listOf("出售价格", "用途", "加工")) {
            violations += "铜矿石立即行动顺序违反物品契约：$labels"
        }
        if (immediate.firstOrNull { it.label == "出售价格" }?.value != "5 金币") {
            violations += "铜矿石出售价格错误：${immediate.firstOrNull { it.label == "出售价格" }?.value}"
        }
        val uses = immediate.firstOrNull { it.label == "用途" }?.value.orEmpty()
        if (!uses.startsWith("樱桃炸弹")) violations += "铜矿石用途未解析为中文：$uses"
        val machines = immediate.firstOrNull { it.label == "加工" }?.value.orEmpty()
        if (!machines.contains("熔炉")) violations += "铜矿石加工未解析为中文：$machines"
        val machineDetails = entry.sections.firstOrNull { it.title == "加工用途详情" }
        if (machineDetails == null || machineDetails.facts.isEmpty()) {
            violations += "铜矿石缺少加工用途详情"
        } else {
            val joined = machineDetails.facts.joinToString("；") { it.value }
            if (!joined.contains("每次 5 个")) violations += "铜矿石加工详情缺少所需数量"
            if (joined.contains("requiredCount") || joined.contains("category_")) {
                violations += "铜矿石加工详情泄露英文条件：$joined"
            }
        }
        for (section in entry.sections) {
            for (fact in section.facts) violations += leaksOf("铜矿石 / ${section.title} / ${fact.label}", fact.value)
        }
        assertTrue("铜矿石条目存在玩家界面泄露：${violations.take(10).joinToString("；")}", violations.isEmpty())
    }

    @Test
    fun quartzMineralFollowsItemContract() = runBlocking {
        val catalogue = installedCatalogue()
        val entry = catalogue.entry("mineral:80").getOrNull() ?: error("真实包缺少 mineral:80")
        val violations = mutableListOf<String>()
        val immediate = entry.sections.firstOrNull { it.title == "立即行动" }?.facts.orEmpty()
        if (immediate.firstOrNull { it.label == "出售价格" }?.value != "25 金币") {
            violations += "石英出售价格错误：${immediate.firstOrNull { it.label == "出售价格" }?.value}"
        }
        val machines = immediate.firstOrNull { it.label == "加工" }?.value.orEmpty()
        if (!machines.contains("熔炉")) violations += "石英加工未解析为中文：$machines"
        val machineDetails = entry.sections.firstOrNull { it.title == "加工用途详情" }
        val joined = machineDetails?.facts?.joinToString("；") { it.value }.orEmpty()
        if (!joined.contains("输入须为：宝石")) {
            violations += "石英加工详情缺少水晶复制器输入条件（输入须为：宝石）：$joined"
        }
        if (joined.contains("category_gem") || joined.contains("requiredCount")) {
            violations += "石英加工详情泄露英文条件：$joined"
        }
        for (section in entry.sections) {
            for (fact in section.facts) violations += leaksOf("石英 / ${section.title} / ${fact.label}", fact.value)
        }
        assertTrue("石英条目存在玩家界面泄露：${violations.take(10).joinToString("；")}", violations.isEmpty())
    }

    @Test
    fun glowRingFollowsRingContract() = runBlocking {
        val catalogue = installedCatalogue()
        val entry = catalogue.entry("ring:516").getOrNull() ?: error("真实包缺少 ring:516")
        val violations = mutableListOf<String>()
        val immediate = entry.sections.firstOrNull { it.title == "立即行动" }?.facts.orEmpty()
        if (immediate.firstOrNull { it.label == "出售价格" }?.value != "100 金币") {
            violations += "小型光辉戒指出售价格错误：${immediate.firstOrNull { it.label == "出售价格" }?.value}"
        }
        val allValues = entry.sections.flatMap { it.facts }.joinToString("；") { it.value }
        if (allValues.contains("种子生产器") || allValues.contains("种子制造器")) {
            violations += "戒指不应出现机器加工噪声：$allValues"
        }
        for (section in entry.sections) {
            for (fact in section.facts) violations += leaksOf("小型光辉戒指 / ${section.title} / ${fact.label}", fact.value)
        }
        assertTrue("小型光辉戒指条目存在玩家界面泄露：${violations.take(10).joinToString("；")}", violations.isEmpty())
    }

    @Test
    fun sneakersFollowFootwearContract() = runBlocking {
        val catalogue = installedCatalogue()
        val entry = catalogue.entry("footwear:504").getOrNull() ?: error("真实包缺少 footwear:504")
        val violations = mutableListOf<String>()
        val immediate = entry.sections.firstOrNull { it.title == "立即行动" }?.facts.orEmpty()
        if (immediate.firstOrNull { it.label == "防御" }?.value != "1") {
            violations += "运动鞋防御错误：${immediate.firstOrNull { it.label == "防御" }?.value}"
        }
        if (immediate.firstOrNull { it.label == "免疫" }?.value != "0") {
            violations += "运动鞋免疫错误：${immediate.firstOrNull { it.label == "免疫" }?.value}"
        }
        if (immediate.firstOrNull { it.label == "购买价格" }?.value != "500 金币") {
            violations += "运动鞋购买价格错误：${immediate.firstOrNull { it.label == "购买价格" }?.value}"
        }
        for (section in entry.sections) {
            for (fact in section.facts) violations += leaksOf("运动鞋 / ${section.title} / ${fact.label}", fact.value)
        }
        assertTrue("运动鞋条目存在玩家界面泄露：${violations.take(8).joinToString("；")}", violations.isEmpty())
    }

    @Test
    fun crystalChairFollowsFurnitureContract() = runBlocking {
        val catalogue = installedCatalogue()
        val entry = catalogue.entry("furniture:131").getOrNull() ?: error("真实包缺少 furniture:131")
        val violations = mutableListOf<String>()
        val immediate = entry.sections.firstOrNull { it.title == "立即行动" }?.facts.orEmpty()
        if (immediate.firstOrNull { it.label == "购买价格" }?.value != "2500 金币") {
            violations += "水晶椅购买价格错误：${immediate.firstOrNull { it.label == "购买价格" }?.value}"
        }
        for (section in entry.sections) {
            for (fact in section.facts) violations += leaksOf("水晶椅 / ${section.title} / ${fact.label}", fact.value)
        }
        assertTrue("水晶椅条目存在玩家界面泄露：${violations.take(8).joinToString("；")}", violations.isEmpty())
    }

    @Test
    fun bakedFishFollowsCookingContract() = runBlocking {
        val catalogue = installedCatalogue()
        val entry = catalogue.entry("cooking_recipe:Baked-Fish").getOrNull()
            ?: error("真实包缺少 cooking_recipe:Baked-Fish")
        val violations = mutableListOf<String>()
        val allFacts = entry.sections.flatMap { it.facts }
        val materials = allFacts.filter { it.label == "材料" }.map { it.value }
        val joined = materials.joinToString("、")
        if (!joined.contains("大麦粉")) violations += "烤鱼缺少大麦粉材料：$joined"
        val page = catalogue.entries(CatalogueQuery("type:cooking_recipe", pageSize = 100)).getOrNull()
        val card = page?.entries?.firstOrNull { it.id == "cooking_recipe:Baked-Fish" }
        if (card == null || card.actionSummary1 == null || !card.actionSummary1!!.contains("材料")) {
            violations += "烤鱼卡片缺少材料摘要"
        }
        for (section in entry.sections) {
            for (fact in section.facts) violations += leaksOf("烤鱼 / ${section.title} / ${fact.label}", fact.value)
        }
        assertTrue("烤鱼条目存在玩家界面泄露：${violations.take(8).joinToString("；")}", violations.isEmpty())
    }

    private fun com.example.stardewoffline.core.model.RelationTarget.displayNameOrNull(): String? =
        (this as? com.example.stardewoffline.core.model.RelationTarget.Entry)?.title

    private suspend fun installedCatalogue(): Schema5WikiCatalogue {
        val arguments = InstrumentationRegistry.getArguments()
        if (arguments.getString(REQUIRED_ARGUMENT) != "true") {
            error("缺少真实包参数：-e $REQUIRED_ARGUMENT true -e $PACKAGE_ARGUMENT <path>")
        }
        val archive = File(requireNotNull(arguments.getString(PACKAGE_ARGUMENT)) { "缺少真实数据包路径" })
        check(archive.isFile) { "真实数据包不存在：$archive" }
        val scenario = TestAppScenario.create(instrumentationTestContext())
        val installed = when (val install = archive.inputStream().use { scenario.dataPackages.installAndActivate(it) }) {
            is AppResult.Success -> install.value
            is AppResult.Failure -> error("真实 schema 5 数据包未能安装：${install.error.message}")
        }
        assertEquals(5, installed.manifest.schemaVersion)
        return Schema5WikiCatalogue(scenario.dataPackages, scenario.schema5ContentRepository)
    }

    private fun submenuLeaks(entity: String, submenu: WikiEntrySubmenu): List<String> {
        val violations = mutableListOf<String>()
        for (group in submenu.groups) {
            for (item in group.items) {
                violations += leaksOf("$entity / ${submenu.title} / ${item.label}", item.label)
                for (detail in item.details) {
                    violations += leaksOf("$entity / ${submenu.title} / ${detail.label}", detail.value)
                }
            }
        }
        return violations
    }

    private fun leaksOf(where: String, value: String): List<String> {
        val violations = FORBIDDEN_PATTERNS.filter { (_, pattern) -> pattern.containsMatchIn(value) }
            .map { (label, _) -> "$where 包含$label：$value" }.toMutableList()
        if (hasRawReferenceSegment(value)) violations += "$where 包含原始实体引用：$value"
        return violations
    }

    private fun hasRawReferenceSegment(value: String): Boolean =
        value.split('；', '，', ' ').any { segment -> RAW_ENTITY_REFERENCE.matches(segment.trim()) }

    private companion object {
        const val REQUIRED_ARGUMENT = "realV5Required"
        const val PACKAGE_ARGUMENT = "realV5PackagePath"

        val APPROVED_CATEGORY_TITLE = Regex("^[\\u4e00-\\u9fff][\\u4e00-\\u9fff、·（）() ]*$")
        val APPROVED_SOURCE_PHRASING = Regex(
            "依据游戏数据整理|依据游戏数据计算|受游戏条件限制|当前数据包暂未收录|官方资料不足，暂时未知",
        )
        val RAW_ENTITY_REFERENCE = Regex("^[a-z_]+:\\d+$")
        val FORBIDDEN_PATTERNS = listOf(
            "未解析引用" to Regex("未解析"),
            "官方分类引用" to Regex("官方分类引用"),
            "类别引用" to Regex("类别引用"),
            "未本地化季节" to Regex("^(Spring|Summer|Fall|Winter) \\d+$"),
            "未本地化性别" to Regex("^(Male|Female)$"),
            "未本地化常住地" to Regex("^(Town|Desert|Mountain|Forest|Beach)$"),
            "日程内部地点代号" to Regex(
                "\\b(SamHouse|JojaMart|CommunityCenter|SeedShop|Hospital|JoshHouse|SebastianRoom|Saloon|Spa)\\b",
            ),
            "日程字符串令牌" to Regex("Strings\\\\"),
            "未本地化鱼类行为" to Regex("^(floater|dart|smooth|mixed|sinker)$"),
            "未本地化鱼类天气" to Regex("^(sunny|rainy|both)$"),
            "捕捞时间原始格式" to Regex("^\\d{3,4} \\d{3,4}$"),
            "证据类型" to Regex("证据 "),
            "转换规则" to Regex("转换 "),
            "修订信息" to Regex("修订 "),
        )
    }
}
