package io.github.soclear.oneuix.hook.util

import java.time.LocalDateTime

/**
 * Adapter between upstream's clock-format hook and MOD's custom clock tokens.
 * The SystemUI hook must call [format] for every clock update instead of caching a
 * DateTimeFormatter, because lunar and Chinese-period tokens are not Java patterns.
 */
internal object StatusBarClockFormatSupport {
    fun format(pattern: String, now: LocalDateTime = LocalDateTime.now()): String =
        runCatching { StatusBarClockFormatter.format(pattern, now) }
            .getOrElse { StatusBarClockFormatter.format("HH:mm", now) }
}
