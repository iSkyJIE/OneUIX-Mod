package io.github.soclear.oneuix.hook.util

import java.time.LocalDate
import java.time.temporal.ChronoUnit

object TraditionalChineseCalendar {
    private val INFO = longArrayOf(
        0x04bd8, 0x04ae0, 0x0a570, 0x054d5, 0x0d260, 0x0d950, 0x16554, 0x056a0, 0x09ad0, 0x055d2,
        0x04ae0, 0x0a5b6, 0x0a4d0, 0x0d250, 0x1d255, 0x0b540, 0x0d6a0, 0x0ada2, 0x095b0, 0x14977,
        0x04970, 0x0a4b0, 0x0b4b5, 0x06a50, 0x06d40, 0x1ab54, 0x02b60, 0x09570, 0x052f2, 0x04970,
        0x06566, 0x0d4a0, 0x0ea50, 0x06e95, 0x05ad0, 0x02b60, 0x186e3, 0x092e0, 0x1c8d7, 0x0c950,
        0x0d4a0, 0x1d8a6, 0x0b550, 0x056a0, 0x1a5b4, 0x025d0, 0x092d0, 0x0d2b2, 0x0a950, 0x0b557,
        0x06ca0, 0x0b550, 0x15355, 0x04da0, 0x0a5d0, 0x14573, 0x052d0, 0x0a9a8, 0x0e950, 0x06aa0,
        0x0aea6, 0x0ab50, 0x04b60, 0x0aae4, 0x0a570, 0x05260, 0x0f263, 0x0d950, 0x05b57, 0x056a0,
        0x096d0, 0x04dd5, 0x04ad0, 0x0a4d0, 0x0d4d4, 0x0d250, 0x0d558, 0x0b540, 0x0b5a0, 0x195a6,
        0x095b0, 0x049b0, 0x0a974, 0x0a4b0, 0x0b27a, 0x06a50, 0x06d40, 0x0af46, 0x0ab60, 0x09570,
        0x04af5, 0x04970, 0x064b0, 0x074a3, 0x0ea50, 0x06b58, 0x055c0, 0x0ab60, 0x096d5, 0x092e0,
        0x0c960, 0x0d954, 0x0d4a0, 0x0da50, 0x07552, 0x056a0, 0x0abb7, 0x025d0, 0x092d0, 0x0cab5,
        0x0a950, 0x0b4a0, 0x0baa4, 0x0ad50, 0x055d9, 0x04ba0, 0x0a5b0, 0x15176, 0x052b0, 0x0a930,
        0x07954, 0x06aa0, 0x0ad50, 0x05b52, 0x04b60, 0x0a6e6, 0x0a4e0, 0x0d260, 0x0ea65, 0x0d530,
        0x05aa0, 0x076a3, 0x096d0, 0x04bd7, 0x04ad0, 0x0a4d0, 0x1d0b6, 0x0d250, 0x0d520, 0x0dd45,
        0x0b5a0, 0x056d0, 0x055b2, 0x049b0, 0x0a577, 0x0a4b0, 0x0aa50, 0x1b255, 0x06d20, 0x0ada0,
    )

    private const val MIN_YEAR = 1900
    private val MAX_YEAR = MIN_YEAR + INFO.size - 1
    private val MONTH_NAMES = arrayOf("正", "二", "三", "四", "五", "六", "七", "八", "九", "十", "冬", "腊")
    private val DAY_NAMES_PREFIX = arrayOf("初", "十", "廿", "三")
    private val DAY_NAMES_SUFFIX = arrayOf("一", "二", "三", "四", "五", "六", "七", "八", "九", "十")
    private val BASE_DATE = LocalDate.of(1900, 1, 31)

    data class LunarInfo(
        val year: Int,
        val month: Int,
        val day: Int,
        val isLeapMonth: Boolean,
    )

    private fun getYearDays(year: Int): Int {
        var i = 0x8000
        var sum = 348
        val yearData = INFO[year - MIN_YEAR]
        while (i > 0x8) {
            if ((yearData and i.toLong()) != 0L) sum++
            i = i shr 1
        }
        return sum + getLeapMonthDays(year)
    }

    private fun getLeapMonthDays(year: Int): Int {
        return if (getLeapMonth(year) != 0) {
            if ((INFO[year - MIN_YEAR] and 0x10000L) != 0L) 30 else 29
        } else 0
    }

    private fun getLeapMonth(year: Int): Int = (INFO[year - MIN_YEAR] and 0xfL).toInt()

    private fun getRegularMonthDays(year: Int, month: Int): Int =
        if ((INFO[year - MIN_YEAR] and (0x10000L shr month)) != 0L) 30 else 29

    private fun formatDay(day: Int): String {
        return when (day) {
            !in 1..30 -> ""
            10 -> "初十"
            20 -> "二十"
            30 -> "三十"
            else -> DAY_NAMES_PREFIX[(day - 1) / 10] + DAY_NAMES_SUFFIX[(day - 1) % 10]
        }
    }

    fun getLunarInfo(gregorianDate: LocalDate = LocalDate.now()): LunarInfo? {
        if (gregorianDate.year !in MIN_YEAR..MAX_YEAR) return null
        var offset = ChronoUnit.DAYS.between(BASE_DATE, gregorianDate)
        if (offset < 0) return null

        var year = MIN_YEAR
        while (year <= MAX_YEAR) {
            val daysInYear = getYearDays(year)
            if (offset < daysInYear) break
            offset -= daysInYear
            year++
        }
        if (year > MAX_YEAR) return null

        val leapMonth = getLeapMonth(year)
        for (month in 1..12) {
            val regularDays = getRegularMonthDays(year, month)
            if (offset < regularDays) {
                return LunarInfo(year, month, (offset + 1).toInt(), false)
            }
            offset -= regularDays
            if (leapMonth == month) {
                val leapDays = getLeapMonthDays(year)
                if (offset < leapDays) {
                    return LunarInfo(year, month, (offset + 1).toInt(), true)
                }
                offset -= leapDays
            }
        }
        return null
    }

    fun getMonthAndDay(gregorianDate: LocalDate = LocalDate.now()): String {
        val info = getLunarInfo(gregorianDate) ?: return "日期超出支持范围 ($MIN_YEAR-$MAX_YEAR)"
        val monthDisplayName = (if (info.isLeapMonth) "闰" else "") + MONTH_NAMES[info.month - 1]
        return "${monthDisplayName}月${formatDay(info.day)}"
    }
}
