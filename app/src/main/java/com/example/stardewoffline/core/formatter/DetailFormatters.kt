package com.example.stardewoffline.core.formatter

object DetailFormatters {
    fun season(value: String) = when (value.lowercase()) {
        "spring" -> "春季"
        "summer" -> "夏季"
        "fall" -> "秋季"
        "winter" -> "冬季"
        else -> value
    }

    fun seasons(values: List<String>) = values.joinToString("、", transform = ::season)

    fun bool(value: Boolean) = if (value) "是" else "否"

    fun chance(value: Double) = if (value in 0.0..1.0) "${(value * 100).toInt()}%" else value.toString()

    fun gameTime(value: Int) = value.takeIf(::isGameTime)?.let { "%02d:%02d".format(it / 100, it % 100) } ?: value.toString()

    fun condition(value: String?) = value?.takeIf(String::isNotBlank)?.let { "条件：$it（由游戏运行时判断）" }

    private fun isGameTime(value: Int) = value >= 0 && value % 100 in 0..59
}
