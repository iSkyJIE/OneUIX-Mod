#!/usr/bin/env python3
"""Port ONLY the pinned production main's MOD status bar to the upstream layout.
Run after merging upstream/main in the correct main-derived working tree.
Never read customizations from an experimental branch.
"""
from pathlib import Path
import re
import subprocess

MAIN = '92312ebcfe7ab36cf18a55e90af8a0f5dfebb314'
ROOT = Path('app/src/main/java/io/github/soclear/oneuix')
HOOK = Path('hook/src/main/java/io/github/soclear/oneuix/hook')

def official(path):
    return subprocess.check_output(['git', 'show', f'{MAIN}:{path}'], text=True)

def section(source, start, end):
    assert source.count(start) == 1, (start, source.count(start))
    assert source.count(end) == 1, (end, source.count(end))
    return source[source.index(start):source.index(end)]

def replace_one(path, old, new):
    text = path.read_text()
    assert text.count(old) == 1, (str(path), repr(old[:70]), text.count(old))
    path.write_text(text.replace(old, new, 1))

old = official(str(ROOT / 'hook/systemui/StatusBar.kt'))
assert 'DoubleLineClockStyle(0.82f, 0.76f, 0.66f, -0.65f)' in old
assert 'DoubleLineClockStyle(0.90f, 0.84f, 0.60f, -0.65f)' in old
assert 'Build.MODEL.startsWith("SM-F966", ignoreCase = true)' in old
assert 'FileObserver.CLOSE_WRITE or FileObserver.MOVED_TO' in old

# Copy all algorithmic and view-state logic directly from production main.
model = section(old, '    private data class DoubleLineClockStyle(', '    private fun View.findStatusBarArea(')
model = model.replace('    private var statusBarPreferenceObserver: FileObserver? = null\n\n', '')
assert 'statusBarPreferenceObserver' not in model
vertical_update = section(old, '    private fun View.findStatusBarArea(', '    private fun observeStatusBarPreference(')
view_and_style = section(old, '    private fun registerStatusBarView(', '    fun setStatusBarPaddingDp(')
doubleline_apply = section(old, '    private fun applyDoubleLineClockText(', '    private fun setStatusBarClockText(')
assert 'fun registerDoubleLineClockView' in view_and_style
assert 'setLineSpacing(extraLineGapPx, doubleLineClockStyle.lineSpacing)' in doubleline_apply

header = '''package io.github.soclear.oneuix.hook.systemui

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
'''

integration = '''    /** Read framework-shared preferences without relying on a soon-closed /proc fd. */
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
                                current.statusBarClockFormat.contains('\\n')) {
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

'''

restore = '''    fun applyDoubleLineClockText(clockTextView: TextView, dateTime: String) {
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
        clockTextView.contentDescription = dateTime.replace('\\n', ' ')
    }
}
'''

new_helper = HOOK / 'systemui/OfficialMainStatusBarMod.kt'
new_helper.parent.mkdir(parents=True, exist_ok=True)
new_helper.write_text(header + model + vertical_update + view_and_style + integration +
                      doubleline_apply + restore)

# Upstream class and hook sequence are retained. Only the MOD clock rendering branch
# and real custom-token formatter are grafted onto the new framework.
status = HOOK / 'systemui/StatusBar.kt'
src = status.read_text()
replace_one(status,
    'import io.github.soclear.oneuix.hook.util.afterAttach\n',
    'import io.github.soclear.oneuix.hook.util.afterAttach\n'
    'import io.github.soclear.oneuix.hook.util.StatusBarClockFormatter\n')
replace_one(status,
    '''        val dateTimeFormatter = try {
            DateTimeFormatter.ofPattern(format)
        } catch (_: Throwable) {
            DateTimeFormatter.ofPattern("HH:mm")
        }
        setStatusBarClockText {
            dateTimeFormatter.format(LocalDateTime.now())
        }
''',
    '''        setStatusBarClockText {
            runCatching {
                StatusBarClockFormatter.format(format, LocalDateTime.now())
            }.getOrElse {
                DateTimeFormatter.ofPattern("HH:mm").format(LocalDateTime.now())
            }
        }
''')
replace_one(status,
    '''                clockTextView?.text = dateTime
                clockTextView?.contentDescription = dateTime
''',
    '''                if (clockTextView != null) {
                    if (dateTime.contains('\\n')) {
                        OfficialMainStatusBarMod.applyDoubleLineClockText(clockTextView, dateTime)
                    } else {
                        OfficialMainStatusBarMod.restoreSingleLineClock(clockTextView, dateTime)
                    }
                }
''')

entry = HOOK / 'Main.kt'
src = entry.read_text()
assert src.count('StatusBar.setStatusBarPaddingDp(leftPaddingDp, rightPaddingDp)') == 1
replace_one(entry,
    '                    StatusBar.setStatusBarPaddingDp(leftPaddingDp, rightPaddingDp)\n',
    '''                    StatusBar.setStatusBarPaddingDp(leftPaddingDp, rightPaddingDp)
                    OfficialMainStatusBarMod.installVerticalPadding(
                        preference.systemUI.statusBar.statusBarTopPaddingDp,
                        preference.systemUI.statusBar.statusBarBottomPaddingDp,
                    )
''')
replace_one(entry,
    'import io.github.soclear.oneuix.hook.systemui.StatusBar\n',
    'import io.github.soclear.oneuix.hook.systemui.StatusBar\n'
    'import io.github.soclear.oneuix.hook.systemui.OfficialMainStatusBarMod\n')
replace_one(entry,
    '''                if (preference.systemUI.statusBar.setStatusBarClockFormat) {
                    val format = preference.systemUI.statusBar.statusBarClockFormat
                    StatusBar.setStatusBarClockFormat(format)
                }

                if (preference.systemUI.statusBar.setStatusBarClockTextScale) {
''',
    '''                val usesDoubleLineClock =
                    preference.systemUI.statusBar.setStatusBarClockFormat &&
                    preference.systemUI.statusBar.statusBarClockFormat.contains('\\n')
                if (preference.systemUI.statusBar.setStatusBarClockFormat) {
                    val bar = preference.systemUI.statusBar
                    OfficialMainStatusBarMod.configureClock(
                        bar.statusBarDoubleLineClockSize,
                        bar.statusBarClockTextScale,
                        bar.doubleLineClockGapDp,
                        bar.useFold7CustomDoubleLineClockScale,
                        bar.fold7DoubleLineClockTimeScale,
                        bar.fold7DoubleLineClockDateScale,
                    )
                    StatusBar.setStatusBarClockFormat(bar.statusBarClockFormat)
                }

                if (preference.systemUI.statusBar.setStatusBarClockTextScale &&
                    !usesDoubleLineClock) {
''')

# Original main's formatter AND calendar must remain byte-for-byte identical,
# with only the source directory changed by the upstream module split.
for name in ('StatusBarClockFormatter.kt', 'TraditionalChineseCalendar.kt'):
    path = HOOK / 'util' / name
    path.parent.mkdir(parents=True, exist_ok=True)
    original = official(str(ROOT / 'hook/util' / name))
    path.write_text(original)

# Keep official main's UI. Git auto-merges upstream event renames/new settings;
# app needs a compile dependency because its clock preview calls the formatter.
gradle = Path('app/build.gradle.kts')
replace_one(gradle, 'runtimeOnly(project(":hook"))', 'implementation(project(":hook"))')
properties = Path('gradle.properties')
replace_one(properties,
    'oneuix.applicationId=io.github.soclear.oneuix',
    'oneuix.applicationId=io.github.mod.oneuix')

# No experimental-branch source is imported. Verify inherited MOD interface.
ui = ROOT / 'ui/category/DetailPaneSystemUI.kt'
ui_src = ui.read_text()
for name in ('StatusBarTopPaddingDp', 'StatusBarBottomPaddingDp',
             'DoubleLineClockGapDp', 'StatusBarDoubleLineClockSize',
             'Fold7DoubleLineClockTimeScale', 'Fold7DoubleLineClockDateScale',
             'StatusBarClockFormatter.format', 'ProcessBuilder('):
    assert name in ui_src, f'Production MOD UI feature lost: {name}'
common = Path('common/src/main/java/io/github/soclear/oneuix/common/Preference.kt').read_text()
for name in ('statusBarTopPaddingDp', 'statusBarBottomPaddingDp',
             'statusBarDoubleLineClockSize', 'doubleLineClockGapDp',
             'fold7DoubleLineClockTimeScale', 'fold7DoubleLineClockDateScale'):
    assert name in common, f'Production preference lost: {name}'
assert 'doubleLineClockGapDp: Float = 1f' in common, 'Production default changed'
assert 'setLineSpacing(extraLineGapPx, doubleLineClockStyle.lineSpacing)' in new_helper.read_text()
assert 'io.github.mod.oneuix' in properties.read_text()
print('MAIN_FIRST_STATUSBAR_PORT: source-derived algorithms, model-gating, settings and app ID preserved')
