from pathlib import Path

STATUSBAR = Path("app/src/main/java/io/github/soclear/oneuix/hook/systemui/StatusBar.kt")
status = STATUSBAR.read_text(encoding="utf-8")

# The verified CNLUNAR patch already injects the last-line fitting helper.
# Replace only that helper with an equal-cell line-height implementation.
old_import = "import android.text.Spanned\n"
new_import = old_import + "import android.text.style.LineHeightSpan\n"
if "import android.text.style.LineHeightSpan" not in status:
    if old_import not in status:
        raise SystemExit("Spanned import not found; no source changed")
    status = status.replace(old_import, new_import, 1)

start = status.find("    private fun fitDoubleLineClockLastLine(")
end_marker = "\n    private fun applyDoubleLineClockText(\n"
end = status.find(end_marker, start)
if start < 0 or end < 0:
    raise SystemExit("Verified last-line helper was not found; no source changed")

helper = '''    private class EqualCellLineHeightSpan(private val cellHeight: Int) : LineHeightSpan {
        override fun chooseHeight(
            text: CharSequence,
            start: Int,
            end: Int,
            spanstartv: Int,
            lineHeight: Int,
            fm: android.graphics.Paint.FontMetricsInt,
        ) {
            val originalHeight = fm.descent - fm.ascent
            if (originalHeight <= 0) return
            val targetHeight = maxOf(cellHeight, originalHeight)
            val extra = targetHeight - originalHeight
            val topExtra = extra / 2
            val bottomExtra = extra - topExtra
            fm.ascent -= topExtra
            fm.descent += bottomExtra
            fm.top = fm.ascent
            fm.bottom = fm.descent
        }
    }

    private fun fitDoubleLineClockLastLine(clockTextView: TextView, baseTranslationY: Float) {
        clockTextView.post {
            val text = clockTextView.text as? android.text.Spannable ?: return@post
            val firstLineEnd = text.toString().indexOf('\\n')
            if (firstLineEnd < 0) return@post
            val viewHeight = clockTextView.height
            if (viewHeight <= 0) return@post

            // Treat the clock viewport like two equal cells: upper half for time,
            // lower half for date. Each paragraph receives its own line-height span.
            val cellHeight = maxOf(1, viewHeight / 2)
            text.getSpans(0, text.length, EqualCellLineHeightSpan::class.java).forEach {
                text.removeSpan(it)
            }
            if (firstLineEnd > 0) {
                text.setSpan(
                    EqualCellLineHeightSpan(cellHeight),
                    0,
                    firstLineEnd,
                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
            if (firstLineEnd + 1 < text.length) {
                text.setSpan(
                    EqualCellLineHeightSpan(cellHeight),
                    firstLineEnd + 1,
                    text.length,
                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
            clockTextView.translationY = baseTranslationY
            clockTextView.requestLayout()
            clockTextView.invalidate()
        }
    }
'''

status = status[:start] + helper + status[end:]
STATUSBAR.write_text(status, encoding="utf-8")

check = STATUSBAR.read_text(encoding="utf-8")
assert "import android.text.style.LineHeightSpan" in check
assert "private class EqualCellLineHeightSpan" in check
assert "val cellHeight = maxOf(1, viewHeight / 2)" in check
assert "fitDoubleLineClockLastLine(clockTextView" in check
print("Verified: Fold7 equal-cell double-line layout patch")
