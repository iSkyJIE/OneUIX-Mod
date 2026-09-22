package io.github.soclear.oneuix.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyPreferenceMigrationTest {
    @Test
    fun oldNotificationFlagsAndModPaddingSurvive() {
        val json = """{"systemUI":{"statusBar":{"statusBarTopPaddingDp":1.5,"statusBarBottomPaddingDp":2.0},"other":{"disableNotificationGrouping":true,"autoExpandNotifications":true,"hideOngoingActivityMedia":true,"hideOngoingActivityMediaPackages":"com.example.music"}}}"""
        val restored = IgnoreUnknownKeysJson.decodeFromString<Preference>(
            LegacyPreferenceMigration.normalize(json)
        )
        assertEquals(1.5f, restored.systemUI.statusBar.statusBarTopPaddingDp)
        assertEquals(2f, restored.systemUI.statusBar.statusBarBottomPaddingDp)
        assertTrue(restored.systemUI.notification.disableNotificationGrouping)
        assertTrue(restored.systemUI.notification.autoExpandNotifications)
        assertTrue(restored.systemUI.notification.hideOngoingActivityMedia)
        assertEquals("com.example.music", restored.systemUI.notification.hideOngoingActivityMediaPackages)
    }

    @Test
    fun existingNewPreferenceWinsOverLegacyValue() {
        val json = """{"systemUI":{"other":{"autoExpandNotifications":true},"notification":{"autoExpandNotifications":false}}}"""
        val restored = IgnoreUnknownKeysJson.decodeFromString<Preference>(
            LegacyPreferenceMigration.normalize(json)
        )
        assertFalse(restored.systemUI.notification.autoExpandNotifications)
    }

    @Test
    fun modernJsonIsNotRewritten() {
        val json = """{"systemUI":{"notification":{"autoExpandNotifications":true}}}"""
        assertEquals(json, LegacyPreferenceMigration.normalize(json))
    }
}
