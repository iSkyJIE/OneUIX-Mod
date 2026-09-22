package io.github.soclear.oneuix.hook.systemui

import android.os.FileObserver
import android.text.SpannableString
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.widget.TextView
import io.github.libxposed.api.XposedModule
import io.github.soclear.oneuix.common.DoubleLineClockSettings
import io.github.soclear.oneuix.common.Preference
import io.github.soclear.oneuix.hook.util.PreferenceProvider
import io.github.soclear.oneuix.hook.util.xlog
import java.io.File
import java.util.WeakHashMap

/** Keeps MOD double-line clock size/gap controls live without restarting SystemUI. */
object StatusBarDoubleLineRuntime {
    private data class Style(
        val upperScale: Float,
        val lowerScale: Float,
        val gapDp: Float,
    )

    @Volatile
    private var style = Style(1f, 1f, 0f)
    private val activeViews = WeakHashMap<TextView, Unit>()
    private var preferenceObserver: FileObserver? = null

    private fun resolveStyle(
        preset: String,
        gapDp: Float,
        independent: Boolean,
        upperScale: Float,
        lowerScale: Float,
    ): Style {
        val presetScale = DoubleLineClockSettings.presetScale(preset)
        return Style(
            upperScale = if (independent) {
                DoubleLineClockSettings.timeScale(upperScale)
            } else presetScale,
            lowerScale = if (independent) {
                DoubleLineClockSettings.dateScale(lowerScale)
            } else presetScale,
            gapDp = DoubleLineClockSettings.lineGapDp(gapDp),
        )
    }

    private fun update(
        preset: String,
        gapDp: Float,
        independent: Boolean,
        upperScale: Float,
        lowerScale: Float,
    ) {
        style = resolveStyle(preset, gapDp, independent, upperScale, lowerScale)
        val views = synchronized(activeViews) { activeViews.keys.toList() }
        views.forEach { view ->
            view.post {
                val currentText = view.text?.toString() ?: return@post
                if (currentText.contains('\n')) apply(view, currentText)
            }
        }
    }

    fun apply(view: TextView, dateTime: String) {
        synchronized(activeViews) { activeViews[view] = Unit }
        val lineBreak = dateTime.indexOf('\n')
        if (lineBreak < 0) return
        val currentStyle = style

        view.setSingleLine(false)
        view.maxLines = 2
        view.minLines = 2
        view.includeFontPadding = false
        view.ellipsize = null
        view.setHorizontallyScrolling(false)
        view.setLineSpacing(
            currentStyle.gapDp * view.resources.displayMetrics.density,
            1f,
        )

        val styledText = SpannableString(dateTime)
        if (lineBreak > 0) {
            styledText.setSpan(
                RelativeSizeSpan(currentStyle.upperScale),
                0,
                lineBreak,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
        if (lineBreak + 1 < styledText.length) {
            styledText.setSpan(
                RelativeSizeSpan(currentStyle.lowerScale),
                lineBreak + 1,
                styledText.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
        view.text = styledText
        view.contentDescription = dateTime.replace('\n', ' ')
        view.requestLayout()
        view.invalidate()
    }

    fun unregister(view: TextView) {
        synchronized(activeViews) { activeViews.remove(view) }
    }

    context(xposedModule: XposedModule)
    private fun watchRemotePreference() {
        if (preferenceObserver != null) return
        try {
            val descriptor = xposedModule.openRemoteFile(Preference.FILE_NAME)
            try {
                val path = File("/proc/self/fd/${descriptor.fd}")
                val watcher = object : FileObserver(path, FileObserver.CLOSE_WRITE) {
                    override fun onEvent(event: Int, changedPath: String?) {
                        if (event and FileObserver.CLOSE_WRITE == 0) return
                        val statusBar = with(xposedModule) {
                            PreferenceProvider.loadPreference()
                        }?.systemUI?.statusBar ?: return
                        if (!statusBar.setStatusBarClockFormat ||
                            !statusBar.statusBarClockFormat.contains('\n')) return
                        val preset = statusBar.statusBarDoubleLineClockSize.ifBlank {
                            DoubleLineClockSettings.fromLegacyScale(
                                statusBar.statusBarClockTextScale
                            )
                        }
                        update(
                            preset = preset,
                            gapDp = statusBar.doubleLineClockGapDp,
                            independent = statusBar.useFold7CustomDoubleLineClockScale,
                            upperScale = statusBar.fold7DoubleLineClockTimeScale,
                            lowerScale = statusBar.fold7DoubleLineClockDateScale,
                        )
                    }
                }
                watcher.startWatching()
                preferenceObserver = watcher
            } finally {
                descriptor.close()
            }
        } catch (t: Throwable) {
            xlog(t)
        }
    }

    context(xposedModule: XposedModule)
    fun install(
        preset: String,
        gapDp: Float,
        independent: Boolean,
        upperScale: Float,
        lowerScale: Float,
    ) {
        update(preset, gapDp, independent, upperScale, lowerScale)
        watchRemotePreference()
    }
}
