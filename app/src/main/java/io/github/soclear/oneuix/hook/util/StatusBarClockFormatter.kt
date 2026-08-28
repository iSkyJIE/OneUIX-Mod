package io.github.soclear.oneuix.hook.util

import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Status-bar clock formatter with OneUIX custom tokens.
 * Standard DateTimeFormatter patterns remain unchanged.
 *
 * Custom tokens:
 * CNLUNAR   - traditional Chinese lunar month and day
 * CNPERIOD  - eight-part day period
 * CNYEAR    - traditional Chinese lunar year
 * CNZODIAC  - Chinese zodiac animal
 */
object StatusBarClockFormatter {
    private const val LUNAR_TOKEN = "CNLUNAR"
    private const val PERIOD_TOKEN = "CNPERIOD"
    private const val YEAR_TOKEN = "CNYEAR"
    private const val ZODIAC_TOKEN = "CNZODIAC"

    private data class LunarInfo(
        val year: Int,
        val month: Int,
        val day: Int,
        val isLeapMonth: Boolean,
    )

    private data class CustomToken(
        val start: Int,
        val length: Int,
        val value: String,
    )

    private fun periodText(time: LocalTime): String = when (time.hour) {
        in 0..4 -> "凌晨"
        in 5..7 -> "早晨"
        in 8..10 -> "上午"
        in 11..12 -> "中午"
        in 13..17 -> "下午"
        in 18..20 -> "傍晚"
        in 21..22 -> "晚上"
        else -> "深夜"
    }

    private fun lunarInfo(date: java.time.LocalDate): LunarInfo? {
        // Keep the authoritative conversion in TraditionalChineseCalendar.
        // The existing class exposes month/day text but not its calculated year.
        // Reconstruct the year from the same 1900-based lunar data used by that class.
        // This helper is intentionally kept local to the formatter test branch.
        val info = TraditionalChineseCalendar.getLunarInfo(date) ?: return null
        return LunarInfo(info.year, info.month, info.day, info.isLeapMonth)
    }

    private fun lunarYearText(info: LunarInfo): String {
        val stems = charArrayOf('甲', '乙', '丙', '丁', '戊', '己', '庚', '辛', '壬', '癸')
        val branches = charArrayOf('子', '丑', '寅', '卯', '辰', '巳', '午', '未', '申', '酉', '戌', '亥')
        val index = (info.year - 4).floorMod(60)
        return "${stems[index % 10]}${branches[index % 12]}年"
    }

    private fun zodiacText(info: LunarInfo): String {
        val zodiac = arrayOf("鼠", "牛", "虎", "兔", "龙", "蛇", "马", "羊", "猴", "鸡", "狗", "猪")
        return zodiac[(info.year - 4).floorMod(12)]
    }

    private fun Int.floorMod(modulus: Int): Int = ((this % modulus) + modulus) % modulus

    fun format(pattern: String, dateTime: LocalDateTime): String {
        val hasCustom = pattern.contains(LUNAR_TOKEN) ||
            pattern.contains(PERIOD_TOKEN) ||
            pattern.contains(YEAR_TOKEN) ||
            pattern.contains(ZODIAC_TOKEN)

        if (!hasCustom) {
            return DateTimeFormatter.ofPattern(pattern).format(dateTime)
        }

        val date = dateTime.toLocalDate()
        val lunarText = if (pattern.contains(LUNAR_TOKEN)) {
            TraditionalChineseCalendar.getMonthAndDay(date)
        } else null
        val info = if (pattern.contains(YEAR_TOKEN) || pattern.contains(ZODIAC_TOKEN)) {
            lunarInfo(date)
        } else null

        val customTokens = buildList {
            fun addAll(token: String, value: String?) {
                if (value == null) return
                var start = pattern.indexOf(token)
                while (start >= 0) {
                    add(CustomToken(start, token.length, value))
                    start = pattern.indexOf(token, start + token.length)
                }
            }
            addAll(LUNAR_TOKEN, lunarText)
            addAll(PERIOD_TOKEN, periodText(dateTime.toLocalTime()))
            addAll(YEAR_TOKEN, info?.let(::lunarYearText))
            addAll(ZODIAC_TOKEN, info?.let(::zodiacText))
        }.sortedBy { it.start }

        val result = StringBuilder()
        var cursor = 0

        customTokens.forEach { token ->
            if (token.start < cursor) return@forEach
            val standardPart = pattern.substring(cursor, token.start)
            if (standardPart.isNotEmpty()) {
                result.append(DateTimeFormatter.ofPattern(standardPart).format(dateTime))
            }
            result.append(token.value)
            cursor = token.start + token.length
        }

        if (cursor < pattern.length) {
            result.append(DateTimeFormatter.ofPattern(pattern.substring(cursor)).format(dateTime))
        }

        return result.toString()
    }
}
