package io.github.soclear.oneuix.hook.systemui

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
