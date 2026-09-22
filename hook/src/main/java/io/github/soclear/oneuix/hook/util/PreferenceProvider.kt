package io.github.soclear.oneuix.hook.util

import android.os.ParcelFileDescriptor
import io.github.libxposed.api.XposedModule
import io.github.soclear.oneuix.common.IgnoreUnknownKeysJson
import io.github.soclear.oneuix.common.LegacyPreferenceMigration
import io.github.soclear.oneuix.common.Preference

object PreferenceProvider {
    // LibXposed 102 remote file: the injected process reads configuration only.
    context(xposedModule: XposedModule)
    fun loadPreference(): Preference? = try {
        val descriptor = xposedModule.openRemoteFile(Preference.FILE_NAME)
        ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { inputStream ->
            if (inputStream.channel.size() == 0L) return null
            IgnoreUnknownKeysJson.decodeFromString<Preference>(
                LegacyPreferenceMigration.normalize(inputStream.readBytes().decodeToString())
            )
        }
    } catch (_: java.io.FileNotFoundException) {
        null
    } catch (t: Throwable) {
        xlog(t)
        null
    }
}
