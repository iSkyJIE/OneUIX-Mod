from pathlib import Path

p = Path('app/src/main/java/io/github/soclear/oneuix/hook/systemui/StatusBar.kt')
s = p.read_text(encoding='utf-8')
start = s.find('    private fun applyDoubleLineClockText(\n')
end = s.find('\n    private fun setStatusBarClockText(\n', start)
if start < 0 or end < 0:
    raise SystemExit('double-line method not found')

block = '''    private data class TwoTextViewsState(
        val parent: ViewGroup,
        val index: Int,
        val originalLayoutParams: ViewGroup.LayoutParams,
        val originalGravity: Int,
        val originalIncludeFontPadding: Boolean,
        val originalPaddingLeft: Int,
        val originalPaddingTop: Int,
        val originalPaddingRight: Int,
        val originalPaddingBottom: Int,
        val originalSingleLine: Boolean,
        val originalMaxLines: Int,
        val originalMinLines: Int,
        val originalEllipsize: android.text.TextUtils.TruncateAt?,
        val originalTextSize: Float,
        val originalTranslationY: Float,
        val originalFontFeatureSettings: String?,
        val originalLetterSpacing: Float,
        val originalTypeface: android.graphics.Typeface?,
        val originalTextColor: Int,
        val originalHintTextColor: Int,
        val dateView: TextView,
        val container: LinearLayout,
    )

    private val twoTextViewsStates = WeakHashMap<TextView, TwoTextViewsState>()

    private fun prepareClockLine(view: TextView) {
        view.setPaddingRelative(0, 0, 0, 0)
        view.gravity = Gravity.CENTER
        view.includeFontPadding = true
        view.ellipsize = null
        view.isSingleLine = true
        view.maxLines = 1
        view.minLines = 1
        view.setHorizontallyScrolling(false)
    }

    private fun ensureTwoTextViews(
        clockTextView: TextView,
        firstLine: String,
        secondLine: String,
        timeScale: Float,
        dateScale: Float,
    ) {
        val state = twoTextViewsStates[clockTextView] ?: run {
            val parent = clockTextView.parent as? ViewGroup ?: return
            val index = parent.indexOfChild(clockTextView)
            if (index < 0) return
            val originalParams = clockTextView.layoutParams
            val container = LinearLayout(clockTextView.context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                clipChildren = false
                clipToPadding = false
            }
            val dateView = TextView(clockTextView.context)
            val saved = TwoTextViewsState(
                parent, index, originalParams, clockTextView.gravity,
                clockTextView.includeFontPadding, clockTextView.paddingLeft,
                clockTextView.paddingTop, clockTextView.paddingRight,
                clockTextView.paddingBottom, clockTextView.isSingleLine,
                clockTextView.maxLines, clockTextView.minLines, clockTextView.ellipsize,
                clockTextView.textSize, clockTextView.translationY,
                clockTextView.fontFeatureSettings, clockTextView.letterSpacing,
                clockTextView.typeface, clockTextView.currentTextColor,
                clockTextView.currentHintTextColor, dateView, container,
            )
            parent.removeViewAt(index)
            container.addView(clockTextView, LinearLayout.LayoutParams(0, 0, 1f).apply { width = ViewGroup.LayoutParams.MATCH_PARENT })
            container.addView(dateView, LinearLayout.LayoutParams(0, 0, 1f).apply { width = ViewGroup.LayoutParams.MATCH_PARENT })
            parent.addView(container, index, originalParams)
            twoTextViewsStates[clockTextView] = saved
            saved
        }
        val dateView = state.dateView
        state.container.translationY = state.originalTranslationY
        prepareClockLine(clockTextView)
        prepareClockLine(dateView)
        clockTextView.setTextSize(TypedValue.COMPLEX_UNIT_PX, state.originalTextSize * timeScale)
        dateView.setTextSize(TypedValue.COMPLEX_UNIT_PX, state.originalTextSize * dateScale)
        clockTextView.text = firstLine
        dateView.text = secondLine
        clockTextView.contentDescription = "$firstLine $secondLine"
        state.container.requestLayout()
        state.container.invalidate()
    }

    private fun restoreTwoTextViews(clockTextView: TextView) {
        val state = twoTextViewsStates.remove(clockTextView) ?: return
        val parent = state.parent
        val container = state.container
        val current = parent.indexOfChild(container)
        if (current >= 0) parent.removeViewAt(current)
        clockTextView.typeface = state.originalTypeface
        clockTextView.setTextColor(state.originalTextColor)
        clockTextView.setHintTextColor(state.originalHintTextColor)
        clockTextView.fontFeatureSettings = state.originalFontFeatureSettings
        clockTextView.letterSpacing = state.originalLetterSpacing
        clockTextView.includeFontPadding = state.originalIncludeFontPadding
        clockTextView.gravity = state.originalGravity
        clockTextView.setPaddingRelative(state.originalPaddingLeft, state.originalPaddingTop, state.originalPaddingRight, state.originalPaddingBottom)
        clockTextView.isSingleLine = state.originalSingleLine
        clockTextView.maxLines = state.originalMaxLines
        clockTextView.minLines = state.originalMinLines
        clockTextView.ellipsize = state.originalEllipsize
        clockTextView.setTextSize(TypedValue.COMPLEX_UNIT_PX, state.originalTextSize)
        clockTextView.translationY = state.originalTranslationY
        parent.addView(clockTextView, state.index.coerceAtMost(parent.childCount), state.originalLayoutParams)
    }

    private fun applyDoubleLineClockText(
        clockTextView: TextView,
        dateTime: String,
        runtimeConfig: DoubleLineClockRuntimeConfig,
    ) {
        val firstLineEnd = dateTime.indexOf('\\n')
        if (firstLineEnd < 0) return
        val style = runtimeConfig.style
        ensureTwoTextViews(
            clockTextView,
            dateTime.substring(0, firstLineEnd),
            dateTime.substring(firstLineEnd + 1),
            style.timeScale,
            style.dateScale,
        )
    }
'''
s = s[:start] + block + s[end:]
old = '''                    singleLineClockLayouts.remove(clockTextView)?.let { original ->
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
'''
new = '''                    restoreTwoTextViews(clockTextView)
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
'''
if old not in s:
    raise SystemExit('single-line restoration block not found')
s = s.replace(old, new, 1)
p.write_text(s, encoding='utf-8')
print('Verified: Fold7 independent two-TextView clock layout patch')
