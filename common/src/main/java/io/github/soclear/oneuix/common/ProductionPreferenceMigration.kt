package io.github.soclear.oneuix.common

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.parseToJsonElement

/** Upstream 1.8 moved four production MOD values from systemUI.other to notification.
 * Keep the old keys and their actual values; only fill fields not already set in the new schema.
 */
object ProductionPreferenceMigration {
    private val notificationKeys = listOf(
        "autoExpandNotifications",
        "disableNotificationGrouping",
        "hideOngoingActivityMedia",
        "hideOngoingActivityMediaPackages",
    )

    fun decode(raw: String): Preference {
        val root = IgnoreUnknownKeysJson.parseToJsonElement(raw) as? JsonObject
            ?: return IgnoreUnknownKeysJson.decodeFromString<Preference>(raw)
        val systemUI = root["systemUI"] as? JsonObject
            ?: return IgnoreUnknownKeysJson.decodeFromString<Preference>(raw)
        val oldOther = systemUI["other"] as? JsonObject
            ?: return IgnoreUnknownKeysJson.decodeFromString<Preference>(raw)
        val existingNotification = systemUI["notification"] as? JsonObject ?: JsonObject(emptyMap())
        val updatedNotification = existingNotification.toMutableMap()
        notificationKeys.forEach { key ->
            if (key !in updatedNotification) {
                oldOther[key]?.let { updatedNotification[key] = it }
            }
        }
        if (updatedNotification == existingNotification) {
            return IgnoreUnknownKeysJson.decodeFromString<Preference>(raw)
        }
        val updatedSystemUI = systemUI.toMutableMap()
        updatedSystemUI["notification"] = JsonObject(updatedNotification)
        val updatedRoot = root.toMutableMap()
        updatedRoot["systemUI"] = JsonObject(updatedSystemUI)
        return IgnoreUnknownKeysJson.decodeFromString<Preference>(JsonObject(updatedRoot).toString())
    }
}
