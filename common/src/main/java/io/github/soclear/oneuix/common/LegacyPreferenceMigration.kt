package io.github.soclear.oneuix.common

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/** Moves the four notification fields from the MOD legacy systemUI.other schema.
 * The destination wins if an existing backup contains both old and new fields.
 * No files are deleted or overwritten by this pure JSON conversion.
 */
object LegacyPreferenceMigration {
    private val notificationKeys = listOf(
        "disableNotificationGrouping",
        "autoExpandNotifications",
        "hideOngoingActivityMedia",
        "hideOngoingActivityMediaPackages",
    )

    fun normalize(raw: String): String {
        val root = Json.parseToJsonElement(raw) as? JsonObject ?: return raw
        val systemUI = root["systemUI"] as? JsonObject ?: return raw
        val oldOther = systemUI["other"] as? JsonObject ?: return raw
        val notification = (systemUI["notification"] as? JsonObject)?.toMutableMap()
            ?: mutableMapOf()
        var migrated = false
        notificationKeys.forEach { key ->
            if (key !in notification) {
                oldOther[key]?.let {
                    notification[key] = it
                    migrated = true
                }
            }
        }
        if (!migrated) return raw
        val updatedSystemUI = systemUI.toMutableMap().apply {
            put("notification", JsonObject(notification))
        }
        return JsonObject(root.toMutableMap().apply {
            put("systemUI", JsonObject(updatedSystemUI))
        }).toString()
    }
}
