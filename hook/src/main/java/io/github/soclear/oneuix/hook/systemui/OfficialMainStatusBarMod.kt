package io.github.soclear.oneuix.hook.systemui

import android.annotation.SuppressLint
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.text.SpannableString
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import io.github.soclear.oneuix.common.Package
import io.github.soclear.oneuix.common.Preference
import io.github.soclear.oneuix.hook.util.PreferenceProvider
import io.github.soclear.oneuix.hook.util.afterAttach
import io.github.soclear.oneuix.hook.util.xlog
import java.util.WeakHashMap
import kotlin.math.roundToInt

/** Main-derived MOD behavior; upstream StatusBar owns only upstream hooks. */
@SuppressLint("PrivateApi", "DiscouragedApi")
object OfficialMainStatusBarMod {
    private data class DoubleLineClockStyle(
        val timeScale: Float,
        val dateScale: Float,
        val lineSpacing: Float,
        val opticalTranslationYDp: Float,
    )

    private data class SingleLineClockLayout(
        val height: Int?,
        val parentGravity: Int?,
        val gravity: Int,
        val includeFontPadding: Boolean,
        val paddingTop: Int,
        val paddingBottom: Int,
    )

    private val singleLineClockLayouts = WeakHashMap<TextView, SingleLineClockLayout>()
    private val doubleLineClockViews = WeakHashMap<TextView, Unit>()

    private data class DoubleLineClockRuntimeConfig(
        val style: DoubleLineClockStyle,
        val extraLineGapDp: Float,
    )

    @Volatile
    private var doubleLineClockRuntimeConfig: DoubleLineClockRuntimeConfig? = null

    private val statusBarOriginalTranslations = WeakHashMap<View, Float>()
    private val statusBarViews = WeakHashMap<View, Unit>()

    private data class StatusBarVerticalOffset(
        val topDp: Float,
        val bottomDp: Float,
    )

    @Volatile
    private var statusBarVerticalOffset = StatusBarVerticalOffset(0f, 0f)

    private fun View.findStatusBarArea(vararg resourceNames: String): View? {
        resourceNames.forEach { resourceName ->
            val id = resources.getIdentifier(resourceName, "id", Package.SYSTEMUI)
            if (id != 0) {
                findViewById<View>(id)?.let { return it }
            }
        }
        return null
    }

    private fun updateStatusBarVerticalOffset(topDp: Float, bottomDp: Float) {
        statusBarVerticalOffset = StatusBarVerticalOffset(topDp, bottomDp)
        val activeStatusBarViews = synchronized(statusBarViews) {
            statusBarViews.keys.toList()
        }
        activeStatusBarViews.forEach { statusBarView ->
            statusBarView.post {
                applyStatusBarVerticalOffset(statusBarView)
            }
        }
    }

    private fun registerStatusBarView(statusBarView: View) {
        val shouldAddLayoutListener = synchronized(statusBarViews) {
            statusBarViews.put(statusBarView, Unit) == null
        }
        if (shouldAddLayoutListener) {
            statusBarView.addOnLayoutChangeListener(
                View.OnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
                    applyStatusBarVerticalOffset(view)
                }
            )
        }
        statusBarView.post {
            applyStatusBarVerticalOffset(statusBarView)
        }
    }

    private fun applyStatusBarVerticalOffset(statusBarView: View) {
        val offset = statusBarVerticalOffset
        val density = statusBarView.resources.displayMetrics.density
        val topPx = (offset.topDp.coerceIn(0f, 8f) * density).roundToInt()
        val bottomPx = (offset.bottomDp.coerceIn(0f, 8f) * density).roundToInt()
        // Samsung has kept PhoneStatusBarView across One UI releases, but the
        // internal left/right area IDs vary by device and firmware. Resolve one
        // container per side so nested aliases cannot receive the offset twice.
        val leftArea = statusBarView.findStatusBarArea(
            "status_bar_left_side",
            "status_bar_start_side",
            "status_bar_start_side_content",
            "status_bar_left_container",
        )
        val rightArea = statusBarView.findStatusBarArea(
            "system_icon_area",
            "status_bar_right_side",
            "status_bar_end_side",
            "status_bar_end_side_content",
            "status_icon_area",
        )
        val leftAndRightContainers = listOfNotNull(leftArea, rightArea).distinct()
        val targets = leftAndRightContainers.ifEmpty { listOf(statusBarView) }
        targets.forEach { target ->
            val originalTranslationY = statusBarOriginalTranslations.getOrPut(target) {
                target.translationY
            }
            // Padding inside fixed-height indicator containers is often ignored by
            // their child layout. Translation works across Samsung screen sizes and
            // One UI layouts: top moves both areas down, bottom moves both areas up.
            target.translationY = originalTranslationY + topPx - bottomPx
        }
    }

    private fun doubleLineClockStyle(
        persistedSize: String,
        legacyPresetScale: Float,
        useFold7CustomScale: Boolean,
        fold7TimeScale: Float,
        fold7DateScale: Float,
    ): DoubleLineClockStyle {
        // V10 stored the five choices in the old floating-point clock-scale setting.
        // Preserve that selection until the user picks a V11 string-backed choice.
        val size = when (persistedSize) {
            "small", "compact", "standard", "large", "extra_large" -> persistedSize
            else -> when {
                legacyPresetScale < 0.925f -> "small"
                legacyPresetScale < 0.975f -> "compact"
                legacyPresetScale < 1.025f -> "standard"
                legacyPresetScale < 1.075f -> "large"
                else -> "extra_large"
            }
        }
        // Fold7 keeps a one-line-height status-bar viewport, while its raw Clock
        // text size is larger than on the slab Galaxy models. The dedicated
        // default preserves the full time size and renders the date at 80%.
        // Custom values are explicit user choices, never height-based fitting.
        if (Build.MODEL.startsWith("SM-F966", ignoreCase = true)) {
            val timeScale = if (useFold7CustomScale) {
                fold7TimeScale.coerceIn(0.50f, 1.10f)
            } else 1.00f
            val dateScale = if (useFold7CustomScale) {
                fold7DateScale.coerceIn(0.50f, 1.10f)
            } else 1.00f
            return DoubleLineClockStyle(timeScale, dateScale, 0.66f, -0.85f)
        }
        return when (size) {
            "small" -> DoubleLineClockStyle(0.74f, 0.68f, 0.72f, -0.65f)
            "compact" -> DoubleLineClockStyle(0.78f, 0.72f, 0.69f, -0.65f)
            "large" -> DoubleLineClockStyle(0.86f, 0.80f, 0.63f, -0.65f)
            "extra_large" -> DoubleLineClockStyle(0.90f, 0.84f, 0.60f, -0.65f)
            else -> DoubleLineClockStyle(0.82f, 0.76f, 0.66f, -0.65f)
        }
    }

    private fun updateDoubleLineClockRuntimeConfig(
        persistedSize: String,
        legacyPresetScale: Float,
        extraLineGapDp: Float,
        useFold7CustomScale: Boolean,
        fold7TimeScale: Float,
        fold7DateScale: Float,
    ) {
        val runtimeConfig = DoubleLineClockRuntimeConfig(
            style = doubleLineClockStyle(
                persistedSize,
                legacyPresetScale,
                useFold7CustomScale,
                fold7TimeScale,
                fold7DateScale,
            ),
            extraLineGapDp = extraLineGapDp.coerceIn(0f, 2f),
        )
        doubleLineClockRuntimeConfig = runtimeConfig
        val activeClockViews = synchronized(doubleLineClockViews) {
            doubleLineClockViews.keys.toList()
        }
        activeClockViews.forEach { clockTextView ->
            clockTextView.post {
                val dateTime = clockTextView.text?.toString() ?: return@post
                if (dateTime.contains('\n')) {
                    applyDoubleLineClockText(clockTextView, dateTime, runtimeConfig)
                }
            }
        }
    }

    private fun registerDoubleLineClockView(clockTextView: TextView) {
        synchronized(doubleLineClockViews) {
            doubleLineClockViews[clockTextView] = Unit
        }
    }

    /** Read framework-shared preferences without relying on a soon-closed /proc fd. */
    private var refreshStarted = false
    private fun watchPreference(module: XposedModule) {
        if (refreshStarted) return
        refreshStarted = true
        val worker = HandlerThread("OneUIXModStatusBarPrefs").apply { start() }
        val background = Handler(worker.looper)
        val main = Handler(Looper.getMainLooper())
        background.post(object : Runnable {
            private var previous: Preference.SystemUI.StatusBar? = null
            override fun run() {
                try {
                    val current = with(module) { PreferenceProvider.loadPreference() }
                        ?.systemUI?.statusBar
                    if (current != null && current != previous) {
                        previous = current
                        main.post {
                            updateStatusBarVerticalOffset(
                                current.statusBarTopPaddingDp, current.statusBarBottomPaddingDp
                            )
                            if (current.setStatusBarClockFormat &&
                                current.statusBarClockFormat.contains('\n')) {
                                updateDoubleLineClockRuntimeConfig(
                                    current.statusBarDoubleLineClockSize,
                                    current.statusBarClockTextScale,
                                    current.doubleLineClockGapDp,
                                    current.useFold7CustomDoubleLineClockScale,
                                    current.fold7DoubleLineClockTimeScale,
                                    current.fold7DoubleLineClockDateScale,
                                )
                            }
                        }
                    }
                } catch (t: Throwable) {
                    with(module) { xlog(t) }
                } finally {
                    background.postDelayed(this, 750L)
                }
            }
        })
    }

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    fun installVerticalPadding(topDp: Float, bottomDp: Float) {
        if (param.packageName != Package.SYSTEMUI) return
        updateStatusBarVerticalOffset(topDp, bottomDp)
        afterAttach {
            try {
                val clazz = classLoader.loadClass(
                    "com.android.systemui.statusbar.phone.PhoneStatusBarView"
                )
                clazz.declaredConstructors.forEach { constructor ->
                    xposedModule.hook(constructor).intercept { chain ->
                        val result = chain.proceed()
                        (chain.thisObject as? View)?.let { registerStatusBarView(it) }
                        result
                    }
                }
                watchPreference(xposedModule)
            } catch (t: Throwable) {
                xlog(t)
            }
        }
    }

    fun configureClock(
        preset: String,
        legacyScale: Float,
        gapDp: Float,
        fold7Custom: Boolean,
        upperScale: Float,
        lowerScale: Float,
    ) {
        updateDoubleLineClockRuntimeConfig(
            preset, legacyScale, gapDp, fold7Custom, upperScale, lowerScale
        )
    }

    private fun applyDoubleLineClockText(
        clockTextView: TextView,
        dateTime: String,
        runtimeConfig: DoubleLineClockRuntimeConfig,
    ) {
        val firstLineEnd = dateTime.indexOf('\n')
        if (firstLineEnd < 0) return
        val doubleLineClockStyle = runtimeConfig.style
        singleLineClockLayouts.getOrPut(clockTextView) {
            val layoutParams = clockTextView.layoutParams
            SingleLineClockLayout(
                height = layoutParams?.height,
                parentGravity = (clockTextView.parent as? LinearLayout)?.gravity,
                gravity = clockTextView.gravity,
                includeFontPadding = clockTextView.includeFontPadding,
                paddingTop = clockTextView.paddingTop,
                paddingBottom = clockTextView.paddingBottom,
            )
        }

        clockTextView.layoutParams?.let { params ->
            params.height = 94
            clockTextView.layoutParams = params
        }
        (clockTextView.parent as? LinearLayout)?.gravity = Gravity.CENTER_VERTICAL
        clockTextView.gravity =
            (clockTextView.gravity and Gravity.VERTICAL_GRAVITY_MASK.inv()) or
                Gravity.CENTER_VERTICAL
        clockTextView.isSingleLine = false
        clockTextView.maxLines = 2
        clockTextView.minLines = 2
        clockTextView.includeFontPadding = false
        clockTextView.ellipsize = null
        clockTextView.setHorizontallyScrolling(false)
        val density = clockTextView.resources.displayMetrics.density
        clockTextView.setPaddingRelative(
            clockTextView.paddingStart,
            0,
            clockTextView.paddingEnd,
            0
        )
        val extraLineGapPx = runtimeConfig.extraLineGapDp * density
        clockTextView.setLineSpacing(extraLineGapPx, doubleLineClockStyle.lineSpacing)
        clockTextView.text = SpannableString(dateTime).apply {
            if (firstLineEnd > 0) {
                setSpan(
                    RelativeSizeSpan(doubleLineClockStyle.timeScale),
                    0,
                    firstLineEnd,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            if (firstLineEnd + 1 < length) {
                setSpan(
                    RelativeSizeSpan(doubleLineClockStyle.dateScale),
                    firstLineEnd + 1,
                    length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }
        clockTextView.contentDescription = dateTime.replace('\n', ' ')
        clockTextView.requestLayout()
        clockTextView.invalidate()
    }

    fun applyDoubleLineClockText(clockTextView: TextView, dateTime: String) {
        registerDoubleLineClockView(clockTextView)
        val config = doubleLineClockRuntimeConfig ?: return
        applyDoubleLineClockText(clockTextView, dateTime, config)
    }

    /** Exactly restore the production main's stored one-line layout state. */
    fun restoreSingleLineClock(clockTextView: TextView, dateTime: String) {
        synchronized(doubleLineClockViews) { doubleLineClockViews.remove(clockTextView) }
        singleLineClockLayouts.remove(clockTextView)?.let { original ->
            clockTextView.layoutParams?.let { params ->
                original.height?.let { params.height = it }
                clockTextView.layoutParams = params
            }
            (clockTextView.parent as? LinearLayout)?.let { parent ->
                original.parentGravity?.let { parent.gravity = it }
            }
            clockTextView.gravity = original.gravity
            clockTextView.includeFontPadding = original.includeFontPadding
            clockTextView.setPaddingRelative(
                clockTextView.paddingStart,
                original.paddingTop,
                clockTextView.paddingEnd,
                original.paddingBottom
            )
        }
        clockTextView.isSingleLine = true
        clockTextView.maxLines = 1
        clockTextView.minLines = 1
        clockTextView.setLineSpacing(0f, 1f)
        clockTextView.setHorizontallyScrolling(false)
        clockTextView.translationY = 0f
        clockTextView.text = dateTime
        clockTextView.contentDescription = dateTime.replace('\n', ' ')
    }
}
