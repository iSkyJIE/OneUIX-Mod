package io.github.soclear.oneuix.hook.systemui

import android.text.SpannableString
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.widget.TextView

/** Applies independent time/date scales without changing the clock's underlying text size. */
internal fun applyDoubleLineClockTypography(
    clock: TextView,
    text: String,
    style: DoubleLineClockStyle,
    extraLineGapDp: Float,
) {
    val separator = text.indexOf('\n')
    if (separator < 0) return

    val styled = SpannableString(text)
    if (separator > 0) {
        styled.setSpan(
            RelativeSizeSpan(style.timeScale),
            0,
            separator,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
    }
    if (separator + 1 < text.length) {
        styled.setSpan(
            RelativeSizeSpan(style.dateScale),
            separator + 1,
            text.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
    }
    clock.text = styled
    clock.setLineSpacing(
        extraLineGapDp.coerceIn(0f, 2f) * clock.resources.displayMetrics.density,
        1f,
    )
}
