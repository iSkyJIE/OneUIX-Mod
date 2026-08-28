package io.github.soclear.oneuix.hook.util

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Status-bar clock formatter with OneUIX custom lunar token support.
 * Standard DateTimeFormatter patterns remain unchanged.
 */
object StatusBarClockFormatter {
    private const val LUNAR_TOKEN = "CNLUNAR"

    fun format(pattern: String, dateTime: LocalDateTime): String {
        if (!pattern.contains(LUNAR_TOKEN)) {
            return DateTimeFormatter.ofPattern(pattern).format(dateTime)
        }

        val lunarText = TraditionalChineseCalendar.getMonthAndDay(dateTime.toLocalDate())
        val result = StringBuilder()
        var cursor = 0

        while (cursor < pattern.length) {
            val tokenStart = pattern.indexOf(LUNAR_TOKEN, cursor)
            if (tokenStart < 0) {
                val tail = pattern.substring(cursor)
                if (tail.isNotEmpty()) {
                    result.append(DateTimeFormatter.ofPattern(tail).format(dateTime))
                }
                break
            }

            val standardPart = pattern.substring(cursor, tokenStart)
            if (standardPart.isNotEmpty()) {
                result.append(DateTimeFormatter.ofPattern(standardPart).format(dateTime))
            }
            result.append(lunarText)
            cursor = tokenStart + LUNAR_TOKEN.length
        }

        return result.toString()
    }
}
