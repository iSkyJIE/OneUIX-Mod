package io.github.soclear.oneuix.ui

import io.github.soclear.oneuix.common.ProductionPreferenceMigration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductionPreferenceMigrationTest {
    @Test
    fun preservesOfficialMainStatusBarAndMovedNotificationValues() {
        val original = """{
            "systemUI": {
                "statusBar": {
                    "statusBarTopPaddingDp": 2.5,
                    "statusBarBottomPaddingDp": 1.5,
                    "setStatusBarClockFormat": true,
                    "statusBarClockFormat": "HH:mm\nCNLUNAR CNTIME",
                    "statusBarDoubleLineClockSize": "compact",
                    "doubleLineClockGapDp": 0.4,
                    "useFold7CustomDoubleLineClockScale": true,
                    "fold7DoubleLineClockTimeScale": 0.9,
                    "fold7DoubleLineClockDateScale": 0.75
                },
                "other": {
                    "autoExpandNotifications": true,
                    "disableNotificationGrouping": true,
                    "hideOngoingActivityMedia": true,
                    "hideOngoingActivityMediaPackages": "music.app"
                }
            }
        }"""
        val restored = ProductionPreferenceMigration.decode(original)
        val status = restored.systemUI.statusBar
        assertEquals(2.5f, status.statusBarTopPaddingDp, 0.001f)
        assertEquals(1.5f, status.statusBarBottomPaddingDp, 0.001f)
        assertTrue(status.setStatusBarClockFormat)
        assertEquals("HH:mm\nCNLUNAR CNTIME", status.statusBarClockFormat)
        assertEquals("compact", status.statusBarDoubleLineClockSize)
        assertEquals(0.4f, status.doubleLineClockGapDp, 0.001f)
        assertTrue(status.useFold7CustomDoubleLineClockScale)
        assertEquals(0.9f, status.fold7DoubleLineClockTimeScale, 0.001f)
        assertEquals(0.75f, status.fold7DoubleLineClockDateScale, 0.001f)
        assertTrue(restored.systemUI.notification.autoExpandNotifications)
        assertTrue(restored.systemUI.notification.disableNotificationGrouping)
        assertTrue(restored.systemUI.notification.hideOngoingActivityMedia)
        assertEquals("music.app", restored.systemUI.notification.hideOngoingActivityMediaPackages)
    }

    @Test
    fun newNotificationSettingsTakePrecedenceOverLegacyDuplicates() {
        val original = """{
            "systemUI": {
                "other": {"autoExpandNotifications": true, "hideOngoingActivityMediaPackages": "old"},
                "notification": {"autoExpandNotifications": false, "hideOngoingActivityMediaPackages": "new"}
            }
        }"""
        val restored = ProductionPreferenceMigration.decode(original)
        assertFalse(restored.systemUI.notification.autoExpandNotifications)
        assertEquals("new", restored.systemUI.notification.hideOngoingActivityMediaPackages)
    }

    @Test
    fun mainDefaultDoubleLineGapRemainsOneDp() {
        assertEquals(1f, ProductionPreferenceMigration.decode("{}").systemUI.statusBar.doubleLineClockGapDp, 0.001f)
    }
}
