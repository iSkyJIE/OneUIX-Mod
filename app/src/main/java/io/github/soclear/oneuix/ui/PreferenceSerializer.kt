package io.github.soclear.oneuix.ui

import android.content.Context
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import io.github.soclear.oneuix.XposedServiceManager
import io.github.soclear.oneuix.common.IgnoreUnknownKeysJson
import io.github.soclear.oneuix.common.Preference
import io.github.soclear.oneuix.common.ProductionPreferenceMigration
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.encodeToStream
import java.io.InputStream
import java.io.OutputStream

/** Never discard the official main's existing `files/datastore/whatever` settings.
 * New libxposed remote settings remain authoritative once populated, unless a
 * local write could not be synchronized; that unsynced local state takes priority.
 */
object PreferenceSerializer : Serializer<Preference> {
    private const val TAG = "PreferenceSerializer"
    private const val SYNC_MARKER = "_modRemoteSynced"

    override val defaultValue: Preference = Preference()

    @OptIn(ExperimentalSerializationApi::class)
    private fun readRemote(): Preference? = try {
        val service = XposedServiceManager.xposedService ?: return null
        val descriptor = service.openRemoteFile(Preference.FILE_NAME)
        ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { source ->
            if (source.channel.size() == 0L) null
            else ProductionPreferenceMigration.decode(source.readBytes().decodeToString())
        }
    } catch (e: Exception) {
        Log.w(TAG, "Remote preferences unavailable; preserving local data", e)
        null
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun writeRemote(value: Preference): Boolean = try {
        val service = XposedServiceManager.xposedService ?: return false
        val descriptor = service.openRemoteFile(Preference.FILE_NAME)
        ParcelFileDescriptor.AutoCloseOutputStream(descriptor).use { destination ->
            destination.channel.truncate(0)
            IgnoreUnknownKeysJson.encodeToStream(Preference.serializer(), value, destination)
            destination.channel.force(true)
        }
        true
    } catch (e: Exception) {
        Log.w(TAG, "Remote sync failed; retained local preferences for retry", e)
        false
    }

    override suspend fun readFrom(input: InputStream): Preference {
        val localBytes = runCatching { input.readBytes() }.getOrNull()
        val localRaw = localBytes?.takeIf { it.isNotEmpty() }?.decodeToString()
        val local = localRaw?.let { raw ->
            runCatching { ProductionPreferenceMigration.decode(raw) }.onFailure {
                Log.e(TAG, "Cannot parse production local preferences", it)
            }.getOrNull()
        }
        val syncMarker = localRaw?.let { raw ->
            runCatching {
                (IgnoreUnknownKeysJson.parseToJsonElement(raw) as? JsonObject)
                    ?.get(SYNC_MARKER)?.jsonPrimitive?.booleanOrNull
            }.getOrNull()
        }
        val unsynced = syncMarker == false
        // Only this project's new writer adds the marker. A pre-upgrade local
        // preference file is authoritative even if a rejected staging build has
        // left a non-default shared preference behind.
        val productionLocal = local != null && syncMarker == null
        val remote = readRemote()
        val selected = ProductionPreferenceMigration.select(
            local = local,
            remote = remote,
            localUnsynced = unsynced,
            localFromProduction = productionLocal,
        )
        if (local != null && selected == local && local != remote &&
            (unsynced || productionLocal || remote == null || remote == defaultValue)) {
            writeRemote(local)
        } else if (localRaw == null && remote == null) {
            // A fresh installation must create the remote configuration too;
            // otherwise the hook's first load finds no file and returns early.
            writeRemote(defaultValue)
        }
        return selected
    }

    override suspend fun writeTo(t: Preference, output: OutputStream) {
        val synchronized = writeRemote(t)
        // DataStore's local output is still written even if libxposed is not bound.
        // Otherwise the pre-upgrade file is left stale and later resets settings.
        val encoded = IgnoreUnknownKeysJson.encodeToString(Preference.serializer(), t)
        val root = IgnoreUnknownKeysJson.parseToJsonElement(encoded) as JsonObject
        val local = JsonObject(root + (SYNC_MARKER to JsonPrimitive(synchronized)))
        output.write(local.toString().encodeToByteArray())
    }
}

val Context.dataStore by dataStore("whatever", PreferenceSerializer)
