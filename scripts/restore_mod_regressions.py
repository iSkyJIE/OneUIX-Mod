#!/usr/bin/env python3
"""One-shot restoration on staging only. Abort on a changed source anchor."""
from pathlib import Path
import subprocess
import re

OLD = '92312ebcfe7ab36cf18a55e90af8a0f5dfebb314'
UI = 'app/src/main/java/io/github/soclear/oneuix/ui/category/DetailPaneSystemUI.kt'
MODEL = 'common/src/main/java/io/github/soclear/oneuix/common/Preference.kt'
MAIN = 'hook/src/main/java/io/github/soclear/oneuix/hook/Main.kt'


def old(path):
    return subprocess.check_output(['git', 'show', f'{OLD}:{path}'], text=True)


def read(path):
    return Path(path).read_text(encoding='utf-8')


def write(path, text):
    Path(path).parent.mkdir(parents=True, exist_ok=True)
    Path(path).write_text(text, encoding='utf-8')


def once(text, old_text, new_text):
    n = text.count(old_text)
    if n != 1:
        raise RuntimeError(f'Expected exactly one occurrence, got {n}: {old_text[:120]!r}')
    return text.replace(old_text, new_text, 1)


def section(text, start, end):
    if text.count(start) != 1:
        raise RuntimeError(f'Unexpected section start: {start}')
    a = text.index(start)
    b = text.index(end, a + len(start))
    return text[a:b]


# Keep old JSON names verbatim, and restore the old line-gap default.
s = read(MODEL)
s = once(s, '            val statusBarRightPaddingDp: Float = 0f,\n',
         '            val statusBarRightPaddingDp: Float = 0f,\n'
         '            val statusBarTopPaddingDp: Float = 0f,\n'
         '            val statusBarBottomPaddingDp: Float = 0f,\n')
s = once(s, '            val doubleLineClockGapDp: Float = 0f,',
         '            val doubleLineClockGapDp: Float = 1f,')
write(MODEL, s)

old_ui = old(UI)
s = read(UI)
needed = ['android.widget.Toast', 'androidx.compose.runtime.rememberCoroutineScope',
          'androidx.compose.ui.platform.LocalContext', 'kotlinx.coroutines.Dispatchers',
          'kotlinx.coroutines.launch', 'kotlinx.coroutines.withContext',
          'java.util.concurrent.TimeUnit']
for imp in needed:
    statement = f'import {imp}\n'
    if statement not in old_ui:
        raise RuntimeError(f'Legacy import missing: {imp}')
    if statement not in s:
        s = once(s, 'import android.os.Build\n', 'import android.os.Build\n' + statement)
s = once(s, '    Column(\n        modifier = modifier\n',
         '    val context = LocalContext.current\n'
         '    val coroutineScope = rememberCoroutineScope()\n'
         '    Column(\n        modifier = modifier\n')
new_button = section(s, '        Button(\n            modifier = Modifier.align(Alignment.CenterHorizontally),',
                     '        DividerText(R.string.status_bar)')
legacy_button = section(old_ui, '        Button(\n            modifier = Modifier.align(Alignment.CenterHorizontally),',
                        '        DividerText(R.string.status_bar)')
s = once(s, new_button, legacy_button)
legacy_controls = section(old_ui, '        StatusBarVerticalPaddingControl(\n',
                          '        Column {\n            var widthScale by remember {')
s = once(s, '        Column {\n            var widthScale by remember {',
         legacy_controls + '        Column {\n            var widthScale by remember {')
legacy_helper = section(old_ui, '@Composable\nprivate fun StatusBarVerticalPaddingControl(',
                        'private fun List<PowerMenuAction>.move(')
s = once(s, 'private fun List<PowerMenuAction>.move(',
         legacy_helper + 'private fun List<PowerMenuAction>.move(')
old_events = section(old_ui, '        @JvmInline\n        value class StatusBarTopPaddingDp(',
                     '        @JvmInline\n        value class SetBatteryIconWidthScale(')
s = once(s, '        @JvmInline\n        value class SetBatteryIconWidthScale(',
         old_events + '        @JvmInline\n        value class SetBatteryIconWidthScale(')
old_reducer = section(old_ui, '            is SystemUIEvent.StatusBar.StatusBarTopPaddingDp -> {',
                      '            is SystemUIEvent.StatusBar.SetBatteryIconWidthScale -> {')
s = once(s, '            is SystemUIEvent.StatusBar.SetBatteryIconWidthScale -> {',
         old_reducer + '            is SystemUIEvent.StatusBar.SetBatteryIconWidthScale -> {')
write(UI, s)

for locale in ('values', 'values-zh'):
    path = f'app/src/main/res/{locale}/strings.xml'
    previous = old(path)
    current = read(path)
    names = ('statusBarTopPaddingDp_title', 'statusBarBottomPaddingDp_title',
             'statusBarTopPaddingDp_summary', 'statusBarBottomPaddingDp_summary')
    extracted = []
    for name in names:
        match = re.search(r'^    <string name="' + re.escape(name) + r'"[^\n]*\n', previous, re.M)
        if not match:
            raise RuntimeError(f'Old resource missing: {name}')
        if f'name="{name}"' in current:
            raise RuntimeError(f'Resource already exists: {name}')
        extracted.append(match.group())
    anchor = re.search(r'^    <string name="statusBarRightPaddingDp_title"[^\n]*\n', current, re.M)
    if not anchor:
        raise RuntimeError('Current right-padding title missing')
    current = once(current, anchor.group(), anchor.group() + ''.join(extracted))
    statuses = []
    for name in ('restartSystemUI_success', 'restartSystemUI_failed'):
        match = re.search(r'^    <string name="' + re.escape(name) + r'"[^\n]*\n', previous, re.M)
        if not match or f'name="{name}"' in current:
            raise RuntimeError(f'Unexpected restart resource: {name}')
        statuses.append(match.group())
    anchor = re.search(r'^    <string name="updateStatusBarClockEverySecond_title"[^\n]*\n', current, re.M)
    if not anchor:
        raise RuntimeError('Current clock resource missing')
    current = once(current, anchor.group(), ''.join(statuses) + anchor.group())
    write(path, current)

s = read(MAIN)
s = once(s, 'import io.github.soclear.oneuix.hook.systemui.StatusBar\n',
         'import io.github.soclear.oneuix.hook.systemui.StatusBar\n'
         'import io.github.soclear.oneuix.hook.systemui.StatusBarVerticalPadding\n')
s = once(s, '                run {\n                    val widthScale = if (preference.systemUI.statusBar.setBatteryIconWidthScale) {',
         '                StatusBarVerticalPadding.install(\n'
         '                    preference.systemUI.statusBar.statusBarTopPaddingDp,\n'
         '                    preference.systemUI.statusBar.statusBarBottomPaddingDp,\n'
         '                )\n\n'
         '                run {\n                    val widthScale = if (preference.systemUI.statusBar.setBatteryIconWidthScale) {')
write(MAIN, s)

helper = Path('hook/src/main/java/io/github/soclear/oneuix/hook/systemui/StatusBarVerticalPadding.kt')
if helper.exists():
    raise RuntimeError('Vertical padding helper already exists')
write(str(helper), '''package io.github.soclear.oneuix.hook.systemui

import android.annotation.SuppressLint
import android.view.View
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import io.github.soclear.oneuix.common.Package
import io.github.soclear.oneuix.hook.util.afterAttach
import io.github.soclear.oneuix.hook.util.xlog
import java.util.WeakHashMap
import kotlin.math.roundToInt

/** Restore MOD vertical offsets separately from upstream's horizontal padding hooks. */
@SuppressLint("PrivateApi", "DiscouragedApi")
object StatusBarVerticalPadding {
    private val originalY = WeakHashMap<View, Float>()
    private val activeViews = WeakHashMap<View, Unit>()
    private var shiftDp = 0f

    private fun View.findArea(vararg names: String): View? {
        for (name in names) {
            val id = resources.getIdentifier(name, "id", Package.SYSTEMUI)
            if (id != 0) findViewById<View>(id)?.let { return it }
        }
        return null
    }

    private fun apply(view: View) {
        val left = view.findArea("status_bar_left_side", "status_bar_start_side",
            "status_bar_start_side_content", "status_bar_left_container")
        val right = view.findArea("system_icon_area", "status_bar_right_side",
            "status_bar_end_side", "status_bar_end_side_content", "status_icon_area")
        val targets = listOfNotNull(left, right).distinct().ifEmpty { listOf(view) }
        val shiftPx = (shiftDp * view.resources.displayMetrics.density).roundToInt()
        targets.forEach { target ->
            val baseline = originalY.getOrPut(target) { target.translationY }
            target.translationY = baseline + shiftPx
        }
    }

    private fun register(view: View) {
        if (activeViews.put(view, Unit) != null) return
        view.addOnLayoutChangeListener { target, _, _, _, _, _, _, _, _ -> apply(target) }
        view.post { apply(view) }
    }

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    fun install(topDp: Float, bottomDp: Float) {
        if (param.packageName != Package.SYSTEMUI) return
        shiftDp = topDp.coerceIn(0f, 8f) - bottomDp.coerceIn(0f, 8f)
        afterAttach {
            try {
                val clazz = classLoader.loadClass(
                    "com.android.systemui.statusbar.phone.PhoneStatusBarView")
                // Constructors work even on firmware where onLayout is inherited.
                clazz.declaredConstructors.forEach { constructor ->
                    xposedModule.hook(constructor).intercept { chain ->
                        val result = chain.proceed()
                        (chain.thisObject as? View)?.let { register(it) }
                        result
                    }
                }
            } catch (t: Throwable) {
                xlog(t)
            }
        }
    }
}
''')
print('Verified legacy snippets and restored vertical padding UI/model/hook plus restart result feedback.')
