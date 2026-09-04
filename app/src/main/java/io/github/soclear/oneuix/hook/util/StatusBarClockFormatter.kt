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
 * CNTIME    - traditional Chinese double-hour (十二时辰)
 * CNYEAR    - traditional Chinese lunar year
 * CNZODIAC  - Chinese zodiac animal
 * CNSEASON  - traditional Chinese season, based on 立春/立夏/立秋/立冬
 */
object StatusBarClockFormatter {
    private const val LUNAR_TOKEN = "CNLUNAR"
    private const val PERIOD_TOKEN = "CNPERIOD"
    private const val TIME_TOKEN = "CNTIME"
    private const val YEAR_TOKEN = "CNYEAR"
    private const val ZODIAC_TOKEN = "CNZODIAC"
    private const val SEASON_TOKEN = "CNSEASON"

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

    private fun traditionalTimeText(time: LocalTime): String {
        val branch = when (time.hour) {
            23, 0 -> "子"
            in 1..2 -> "丑"
            in 3..4 -> "寅"
            in 5..6 -> "卯"
            in 7..8 -> "辰"
            in 9..10 -> "巳"
            in 11..12 -> "午"
            in 13..14 -> "未"
            in 15..16 -> "申"
            in 17..18 -> "酉"
            in 19..20 -> "戌"
            else -> "亥"
        }
        return "${branch}时"
    }

    /** Traditional seasons are bounded by 立春、立夏、立秋、立冬. */
    private fun seasonText(date: java.time.LocalDate): String = when (date.monthValue) {
        3, 4 -> "春"
        6, 7 -> "夏"
        9, 10 -> "秋"
        1, 12 -> "冬"
        2 -> if (date.dayOfMonth >= 4) "春" else "冬"
        5 -> if (date.dayOfMonth >= 6) "夏" else "春"
        8 -> if (date.dayOfMonth >= 8) "秋" else "夏"
        11 -> if (date.dayOfMonth >= 8) "冬" else "秋"
        else -> "冬"
    }

    private fun lunarInfo(date: java.time.LocalDate): LunarInfo? {
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
            pattern.contains(TIME_TOKEN) ||
            pattern.contains(YEAR_TOKEN) ||
            pattern.contains(ZODIAC_TOKEN) ||
            pattern.contains(SEASON_TOKEN)

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
            addAll(TIME_TOKEN, traditionalTimeText(dateTime.toLocalTime()))
            addAll(YEAR_TOKEN, info?.let(::lunarYearText))
            addAll(ZODIAC_TOKEN, info?.let(::zodiacText))
            addAll(SEASON_TOKEN, seasonText(date))
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
