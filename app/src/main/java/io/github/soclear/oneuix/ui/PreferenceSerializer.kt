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
        val unsynced = localRaw?.let { raw ->
            runCatching {
                ((IgnoreUnknownKeysJson.parseToJsonElement(raw) as? JsonObject)
                    ?.get(SYNC_MARKER)?.jsonPrimitive?.booleanOrNull == false)
            }.getOrDefault(false)
        } ?: false
        val remote = readRemote()
        val selected = when {
            unsynced && local != null -> local
            remote != null && remote != defaultValue -> remote
            local != null && local != defaultValue -> local
            remote != null -> remote
            local != null -> local
            else -> defaultValue
        }
        if (local != null && selected == local && (unsynced || remote == null || remote == defaultValue)
            && local != remote) {
            writeRemote(local)
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
