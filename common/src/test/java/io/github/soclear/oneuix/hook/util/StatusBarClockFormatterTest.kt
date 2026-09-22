package io.github.soclear.oneuix.hook.util

import java.time.LocalDateTime
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusBarClockFormatterTest {
    @Test fun expandsEveryModTokenAndKeepsDoubleLine() {
        val pattern = "HH:mm\nCNLUNAR CNPERIOD CNTIME CNYEAR CNZODIAC CNSEASON"
        val result = StatusBarClockFormatter.format(pattern, LocalDateTime.of(2026, 9, 22, 12, 34))
        assertTrue(result.startsWith("12:34\n"))
        assertTrue(result.contains("中午"))
        assertTrue(result.contains("午时"))
        assertTrue(result.contains("秋"))
        for (token in listOf("CNLUNAR", "CNPERIOD", "CNTIME", "CNYEAR", "CNZODIAC", "CNSEASON")) {
            assertFalse(result.contains(token))
        }
    }

    @Test fun repeatedTokensExpandAndNormalPatternsStillWork() {
        val date = LocalDateTime.of(2026, 9, 22, 23, 0)
        assertTrue(StatusBarClockFormatter.format("CNTIME / CNTIME", date) == "子时 / 子时")
        assertTrue(StatusBarClockFormatter.format("HH:mm", date) == "23:00")
    }
}
