package io.github.soclear.oneuix.ui

import io.github.soclear.oneuix.hook.util.StatusBarClockFormatter
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Exercise the formatter copied verbatim from production main, never the old test branch. */
class ProductionStatusBarClockRegressionTest {
    @Test
    fun allSixOfficialTokensExpandAcrossTwoClockLines() {
        val pattern = "HH:mm\nCNLUNAR CNPERIOD CNTIME CNYEAR CNZODIAC CNSEASON"
        val dateTime = LocalDateTime.of(2026, 9, 22, 12, 34)
        val result = StatusBarClockFormatter.format(pattern, dateTime)
        assertTrue(result.startsWith("12:34\n"))
        assertTrue(result.contains("中午"))
        assertTrue(result.contains("午时"))
        assertTrue(result.contains("丙午年"))
        assertTrue(result.contains("马"))
        assertTrue(result.contains("秋"))
        for (token in listOf("CNLUNAR", "CNPERIOD", "CNTIME", "CNYEAR", "CNZODIAC", "CNSEASON")) {
            assertFalse("Unexpanded production token: $token", result.contains(token))
        }
    }

    @Test
    fun repeatedChineseTimeTokensAndOrdinaryPatternsRemainStable() {
        val dateTime = LocalDateTime.of(2026, 9, 22, 23, 0)
        assertEquals("子时 / 子时", StatusBarClockFormatter.format("CNTIME / CNTIME", dateTime))
        assertEquals("23:00", StatusBarClockFormatter.format("HH:mm", dateTime))
    }
}
