package io.github.soclear.oneuix.hook.systemui

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.FileObserver
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.text.SpannableString
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedBridge.hookAllConstructors
import de.robv.android.xposed.XposedBridge.hookAllMethods
import de.robv.android.xposed.XposedHelpers.callMethod
import de.robv.android.xposed.XposedHelpers.findAndHookMethod
import de.robv.android.xposed.XposedHelpers.findClass
import de.robv.android.xposed.XposedHelpers.findClassIfExists
import de.robv.android.xposed.XposedHelpers.getIntField
import de.robv.android.xposed.XposedHelpers.getObjectField
import de.robv.android.xposed.XposedHelpers.setObjectField
import de.robv.android.xposed.callbacks.XC_InitPackageResources.InitPackageResourcesParam
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import io.github.soclear.oneuix.data.ONE_UI_VERSION
import io.github.soclear.oneuix.data.Package
import io.github.soclear.oneuix.hook.util.PreferenceProvider
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.WeakHashMap
import kotlin.math.roundToInt

object StatusBar {
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

    private var statusBarPreferenceObserver: FileObserver? = null

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

    private fun observeStatusBarPreference() {
        if (statusBarPreferenceObserver != null) return
        val preferenceFile = PreferenceProvider.getPreferenceFile()
        val parentDirectory = preferenceFile.parentFile ?: return
        statusBarPreferenceObserver = object : FileObserver(
            parentDirectory,
            FileObserver.CLOSE_WRITE or FileObserver.MOVED_TO,
        ) {
            override fun onEvent(event: Int, path: String?) {
                if (path != preferenceFile.name) return
                Handler(Looper.getMainLooper()).post {
                    PreferenceProvider.readPreference()?.systemUI?.statusBar?.let { statusBar ->
                        updateStatusBarVerticalOffset(
                            statusBar.statusBarTopPaddingDp,
                            statusBar.statusBarBottomPaddingDp,
                        )
                        if (
                            statusBar.setStatusBarClockFormat &&
                            statusBar.statusBarClockFormat.contains('\n')
                        ) {
                            updateDoubleLineClockRuntimeConfig(
                                statusBar.statusBarDoubleLineClockSize,
                                statusBar.statusBarClockTextScale,
                                statusBar.doubleLineClockGapDp,
                                statusBar.useFold7CustomDoubleLineClockScale,
                                statusBar.fold7DoubleLineClockTimeScale,
                                statusBar.fold7DoubleLineClockDateScale,
                            )
                        }
                    }
                }
            }
        }.also { it.startWatching() }
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
            } else 0.80f
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

    fun setStatusBarPaddingDp(loadPackageParam: LoadPackageParam, left: Float?, right: Float?) {
        if (loadPackageParam.packageName != io.github.soclear.oneuix.data.Package.SYSTEMUI ||
            left == null && right == null
        ) {
            return
        }
        try {
            val clazz = findClass(
                "com.android.systemui.statusbar.phone.IndicatorGardenAlgorithmCenterCutout",
                loadPackageParam.classLoader
            )
            if (left != null) {
                findAndHookMethod(
                    clazz,
                    "calculateLeftPadding",
                    object : XC_MethodReplacement() {
                        override fun replaceHookedMethod(param: MethodHookParam): Int {
                            val inputProperties =
                                getObjectField(param.thisObject, "inputProperties")
                            val density = getObjectField(inputProperties, "density") as Float
                            return (left * density).roundToInt()
                        }
                    }
                )
            }
            if (right != null) {
                findAndHookMethod(
                    clazz,
                    "calculateRightPadding",
                    object : XC_MethodReplacement() {
                        override fun replaceHookedMethod(param: MethodHookParam): Int {
                            val inputProperties =
                                getObjectField(param.thisObject, "inputProperties")
                            val density = getObjectField(inputProperties, "density") as Float
                            return (right * density).roundToInt()
                        }
                    }
                )
            }
        } catch (t: Throwable) {
            XposedBridge.log(t)
        }
    }

    fun setStatusBarVerticalPadding(
        loadPackageParam: LoadPackageParam,
        topDp: Float,
        bottomDp: Float,
    ) {
        if (loadPackageParam.packageName != Package.SYSTEMUI) return
        updateStatusBarVerticalOffset(topDp, bottomDp)
        observeStatusBarPreference()
        val phoneStatusBarViewClass = findClassIfExists(
            "com.android.systemui.statusbar.phone.PhoneStatusBarView",
            loadPackageParam.classLoader
        ) ?: return
        try {
            // Some One UI builds inherit onLayout() instead of declaring it on
            // PhoneStatusBarView. Hooking that method can therefore be a no-op.
            // Constructors are stable, and the listener runs after every layout.
            hookAllConstructors(phoneStatusBarViewClass, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val statusBarView = param.thisObject as? View ?: return
                    registerStatusBarView(statusBarView)
                }
            })
        } catch (t: Throwable) {
            XposedBridge.log(t)
        }
    }

    fun setBatteryIconScale(
        loadPackageParam: LoadPackageParam,
        widthScale: Float?,
        heightScale: Float?
    ) {
        if (loadPackageParam.packageName != io.github.soclear.oneuix.data.Package.SYSTEMUI || widthScale == null && heightScale == null) return
        try {
            findAndHookMethod(
                "com.android.systemui.battery.BatteryMeterView",
                loadPackageParam.classLoader,
                "scaleBatteryMeterViewsLegacy",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val mBatteryIconView =
                            getObjectField(param.thisObject, "mBatteryIconView") as ImageView
                        mBatteryIconView.layoutParams = mBatteryIconView.layoutParams.apply {
                            if (widthScale != null) {
                                width = (width * widthScale).roundToInt()
                            }
                            if (heightScale != null) {
                                height = (height * heightScale).roundToInt()
                            }
                        }
                    }
                }
            )
        } catch (t: Throwable) {
            XposedBridge.log(t)
        }
    }

    fun hideBatteryPercentageSign(resparam: InitPackageResourcesParam) {
        if (resparam.packageName != io.github.soclear.oneuix.data.Package.SYSTEMUI ||
            Build.VERSION.SDK_INT > Build.VERSION_CODES.UPSIDE_DOWN_CAKE
        ) {
            return
        }
        val batterMeterFormat = "status_bar_settings_${
            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.UPSIDE_DOWN_CAKE) "uniform_"
            else ""
        }battery_meter_format"
        resparam.res.setReplacement(Package.SYSTEMUI, "string", batterMeterFormat, "%d")
    }

    fun updateStatusBarClockEverySecond(loadPackageParam: LoadPackageParam) {
        if (loadPackageParam.packageName != Package.SYSTEMUI) return
        // 每秒更新
        findAndHookMethod(
            "com.android.systemui.statusbar.policy.QSClockQuickStarHelper",
            loadPackageParam.classLoader,
            "updateSecondsClockHandler",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val mSecondsHandler = getObjectField(param.thisObject, "mSecondsHandler")
                    if (mSecondsHandler != null) return
                    val looper = Looper.myLooper() ?: return
                    val handler = Handler(looper)
                    setObjectField(param.thisObject, "mSecondsHandler", handler)
                    val mSecondTick = getObjectField(param.thisObject, "mSecondTick") as Runnable
                    handler.post(mSecondTick)
                }
            }
        )

        // 数字字体等宽
        findAndHookMethod(
            "com.android.systemui.statusbar.policy.QSClockIndicatorViewController",
            loadPackageParam.classLoader,
            "onViewAttached",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val clockTextView = getObjectField(param.thisObject, "view") as TextView
                    clockTextView.fontFeatureSettings = "tnum"
                }
            }
        )
    }

    fun setStatusBarClockTextScale(loadPackageParam: LoadPackageParam, scale: Float) {
        if (loadPackageParam.packageName != Package.SYSTEMUI) return
        // The controller restores the system baseline size before this callback, so scaling
        // remains stable across density and font-scale changes instead of accumulating.
        val callback = object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val clockView = getObjectField(param.thisObject, "view") as TextView
                clockView.setTextSize(TypedValue.COMPLEX_UNIT_PX, clockView.textSize * scale)
            }
        }
        try {
            findAndHookMethod(
                "com.android.systemui.statusbar.policy.QSClockIndicatorViewController",
                loadPackageParam.classLoader,
                "onDensityOrFontScaleChanged",
                callback
            )
        } catch (t: Throwable) {
            XposedBridge.log(t)
        }
    }

    fun setStatusBarClockFormat(
        loadPackageParam: LoadPackageParam,
        format: String,
        doubleLineClockSize: String,
        legacyDoubleLinePresetScale: Float,
        doubleLineClockGapDp: Float,
        useFold7CustomScale: Boolean,
        fold7TimeScale: Float,
        fold7DateScale: Float,
    ) {
        if (loadPackageParam.packageName != Package.SYSTEMUI) return
        val dateTimeFormatter = try {
            DateTimeFormatter.ofPattern(format)
        } catch (_: Throwable) {
            DateTimeFormatter.ofPattern("HH:mm")
        }
        updateDoubleLineClockRuntimeConfig(
            doubleLineClockSize,
            legacyDoubleLinePresetScale,
            doubleLineClockGapDp,
            useFold7CustomScale,
            fold7TimeScale,
            fold7DateScale,
        )
        observeStatusBarPreference()
        setStatusBarClockText(
            loadPackageParam,
        ) {
            dateTimeFormatter.format(LocalDateTime.now())
        }
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
            params.height = ViewGroup.LayoutParams.MATCH_PARENT
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
        clockTextView.translationY = doubleLineClockStyle.opticalTranslationYDp * density
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

    private fun setStatusBarClockText(
        loadPackageParam: LoadPackageParam,
        block: () -> String,
    ) {
        if (loadPackageParam.packageName != Package.SYSTEMUI) return
        val callback = object : XC_MethodReplacement() {
            override fun replaceHookedMethod(param: MethodHookParam): Any? {
                val clockTextView = param.thisObject as TextView
                val dateTime = block()
                val firstLineEnd = dateTime.indexOf('\n')
                if (firstLineEnd >= 0) {
                    val runtimeConfig = doubleLineClockRuntimeConfig ?: return null
                    registerDoubleLineClockView(clockTextView)
                    applyDoubleLineClockText(clockTextView, dateTime, runtimeConfig)
                } else {
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
                }
                clockTextView.contentDescription = dateTime.replace('\n', ' ')
                return null
            }
        }
        try {
            findAndHookMethod(
                "com.android.systemui.statusbar.policy.QSClockIndicatorView",
                loadPackageParam.classLoader,
                "notifyTimeChanged",
                "com.android.systemui.statusbar.policy.QSClockBellSound",
                callback
            )
        } catch (t: Throwable) {
            XposedBridge.log(t)
        }
    }

    fun hideSecureFolderStatusBarIcon(loadPackageParam: LoadPackageParam) {
        if (loadPackageParam.packageName != Package.SYSTEMUI) return
        val callback = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (param.args[0] == "managed_profile") {
                    param.result = null
                }
            }
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                findAndHookMethod(
                    "com.android.systemui.statusbar.phone.ui.StatusBarIconControllerImpl",
                    loadPackageParam.classLoader,
                    "setIcon",
                    String::class.java,
                    "com.android.systemui.statusbar.phone.StatusBarIconHolder",
                    callback
                )
            } else {
                findAndHookMethod(
                    "com.android.systemui.statusbar.phone.StatusBarIconControllerImpl",
                    loadPackageParam.classLoader,
                    "setIcon",
                    String::class.java,
                    Int::class.javaPrimitiveType,
                    CharSequence::class.java,
                    callback
                )
            }
        } catch (t: Throwable) {
            XposedBridge.log(t)
        }
    }

    fun restoreBluetoothStatusBarIcon(loadPackageParam: LoadPackageParam) {
        if (loadPackageParam.packageName != Package.SYSTEMUI) return
        try {
            findAndHookMethod(
                "com.android.systemui.statusbar.phone.ui.StatusBarIconControllerImpl",
                loadPackageParam.classLoader,
                "hideBySimplification",
                "com.android.systemui.statusbar.phone.ui.IconManager",
                String::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val slot = param.args[1] as? String ?: return
                        if (slot == "bluetooth" || slot == "bluetooth_connected") {
                            param.result = false
                        }
                    }
                }
            )
        } catch (t: Throwable) {
            XposedBridge.log(t)
        }
    }

    fun doubleTapStatusBarToSleep(loadPackageParam: LoadPackageParam) {
        val callback = object : XC_MethodHook() {
            var lastTapTime = 0L

            override fun beforeHookedMethod(param: MethodHookParam) {
                val event = param.args[0] as MotionEvent
                if (event.action != MotionEvent.ACTION_DOWN) {
                    return
                }
                val currentTime = System.nanoTime()
                val interval = currentTime - lastTapTime
                if (interval in 40_000_000L..300_000_000L) {
                    lastTapTime = 0L
                    val view = param.thisObject as View
                    lockScreen(view.context)
                    param.result = true
                } else {
                    lastTapTime = currentTime
                }
            }

            fun lockScreen(context: Context) {
                val powerManager = context.getSystemService(PowerManager::class.java)
                callMethod(powerManager, "goToSleep", SystemClock.uptimeMillis())
            }
        }
        try {
            findAndHookMethod(
                "com.android.systemui.statusbar.phone.PhoneStatusBarView",
                loadPackageParam.classLoader,
                "onTouchEvent",
                MotionEvent::class.java,
                callback
            )
        } catch (t: Throwable) {
            XposedBridge.log(t)
        }
    }

    fun hideLockscreenStatusBar(loadPackageParam: LoadPackageParam) {
        if (loadPackageParam.packageName != Package.SYSTEMUI) return
        try {
            findAndHookMethod(
                "com.android.systemui.statusbar.phone.KeyguardStatusBarView",
                loadPackageParam.classLoader,
                "setVisibility",
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        param.args[0] = View.GONE
                    }
                }
            )
        } catch (t: Throwable) {
            XposedBridge.log(t)
        }
    }

    fun setCustomCarrierName(loadPackageParam: LoadPackageParam, carrierName: String) {
        if (loadPackageParam.packageName != Package.SYSTEMUI) return
        try {
            findAndHookMethod(
                "com.android.keyguard.CarrierTextManager",
                loadPackageParam.classLoader,
                "postToCallback",
                $$"com.android.keyguard.CarrierTextManager$CarrierTextCallbackInfo",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val carrierTextCallbackInfo = param.args[0] ?: return
                        runCatching { setObjectField(carrierTextCallbackInfo, "carrierText", carrierName) }
                        runCatching { setObjectField(carrierTextCallbackInfo, "carrierTextShort", carrierName) }
                    }
                }
            )
        } catch (t: Throwable) {
            XposedBridge.log(t)
        }
    }

    fun addBatteryLevelText(
        loadPackageParam: LoadPackageParam,
        hidePercentSign: Boolean,
        hideChargingIcon: Boolean,
    ) {
        if (loadPackageParam.packageName != Package.SYSTEMUI || ONE_UI_VERSION < 70000) return
        val batteryMeterViewClass = findClassIfExists(
            "com.android.systemui.battery.BatteryMeterView",
            loadPackageParam.classLoader
        ) ?: return

        val viewId = View.generateViewId()

        try {
            findAndHookMethod(
                batteryMeterViewClass,
                "scaleBatteryMeterViewsLegacy",
                object : XC_MethodHook() {
                    @SuppressLint("SetTextI18n")
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val batteryMeterView = param.thisObject as ViewGroup
                            var textView = batteryMeterView.findViewById<TextView>(viewId)
                            if (textView == null) {
                                textView = TextView(batteryMeterView.context).apply {
                                    id = viewId
                                    gravity = Gravity.CENTER
                                }
                                batteryMeterView.addView(
                                    textView, LinearLayout.LayoutParams(
                                        ViewGroup.LayoutParams.WRAP_CONTENT,
                                        ViewGroup.LayoutParams.WRAP_CONTENT
                                    )
                                )
                            }
                            val level = getIntField(batteryMeterView, "mLevel")
                            val percent = if (hidePercentSign) "$level" else "$level%"
                            val isCharging = callMethod(batteryMeterView, "isCharging") as Boolean
                            val suffix = if (isCharging && !hideChargingIcon) "\u26A1\uFE0E" else ""
                            textView.text = "$percent$suffix"
                            textView.setTextColor(getIntField(batteryMeterView, "mTextColor"))
                        } catch (t: Throwable) {
                            XposedBridge.log(t)
                        }
                    }
                }
            )
        } catch (t: Throwable) {
            XposedBridge.log(t)
        }

        try {
            hookAllMethods(batteryMeterViewClass, "updateColors", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    try {
                        val view = param.thisObject as ViewGroup
                        val textView = view.findViewById<TextView>(viewId) ?: return
                        textView.setTextColor(getIntField(view, "mTextColor"))
                    } catch (t: Throwable) {
                        XposedBridge.log(t)
                    }
                }
            })
        } catch (t: Throwable) {
            XposedBridge.log(t)
        }
    }
}
