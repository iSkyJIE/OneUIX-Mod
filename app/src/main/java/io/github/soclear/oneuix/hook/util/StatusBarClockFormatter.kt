package io.github.soclear.oneuix.hook.util

import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Status-bar clock formatter with OneUIX custom tokens.
 * Standard DateTimeFormatter patterns remain unchanged.
 *
 * Custom tokens:
 * CNLUNAR  - traditional Chinese lunar month and day
 * CNPERIOD - eight-part day period
 */
object StatusBarClockFormatter {
    private const val LUNAR_TOKEN = "CNLUNAR"
    private const val PERIOD_TOKEN = "CNPERIOD"

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

    fun format(pattern: String, dateTime: LocalDateTime): String {
        if (!pattern.contains(LUNAR_TOKEN) && !pattern.contains(PERIOD_TOKEN)) {
            return DateTimeFormatter.ofPattern(pattern).format(dateTime)
        }

        val customTokens = buildList {
            var lunarStart = pattern.indexOf(LUNAR_TOKEN)
            while (lunarStart >= 0) {
                add(
                    CustomToken(
                        lunarStart,
                        LUNAR_TOKEN.length,
                        TraditionalChineseCalendar.getMonthAndDay(dateTime.toLocalDate()),
                    )
                )
                lunarStart = pattern.indexOf(LUNAR_TOKEN, lunarStart + LUNAR_TOKEN.length)
            }

            var periodStart = pattern.indexOf(PERIOD_TOKEN)
            while (periodStart >= 0) {
                add(
                    CustomToken(
                        periodStart,
                        PERIOD_TOKEN.length,
                        periodText(dateTime.toLocalTime()),
                    )
                )
                periodStart = pattern.indexOf(PERIOD_TOKEN, periodStart + PERIOD_TOKEN.length)
            }
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
            result.append(
                DateTimeFormatter.ofPattern(pattern.substring(cursor)).format(dateTime)
            )
        }

        return result.toString()
    }
}
