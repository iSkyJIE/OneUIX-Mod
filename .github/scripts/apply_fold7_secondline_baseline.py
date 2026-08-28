from pathlib import Path

path = Path("app/src/main/java/io/github/soclear/oneuix/hook/systemui/StatusBar.kt")
s = path.read_text(encoding="utf-8")

needle = "import android.text.style.RelativeSizeSpan\n"
replacement = needle + "import android.text.style.MetricAffectingSpan\n"
if "import android.text.style.MetricAffectingSpan" not in s:
    if needle not in s:
        raise SystemExit("RelativeSizeSpan import not found; no source changed")
    s = s.replace(needle, replacement, 1)

marker = "    private data class SingleLineClockLayout(\n"
insert = '''    private class Fold7SecondLineBaselineSpan(private val shiftPx: Int) : MetricAffectingSpan() {
        override fun updateMeasureState(textPaint: android.text.TextPaint) {
            textPaint.baselineShift -= shiftPx
        }

        override fun updateDrawState(textPaint: android.text.TextPaint) {
            textPaint.baselineShift -= shiftPx
        }
    }

'''
if "private class Fold7SecondLineBaselineSpan" not in s:
    if marker not in s:
        raise SystemExit("SingleLineClockLayout marker not found; no source changed")
    s = s.replace(marker, insert + marker, 1)

old = '''            if (firstLineEnd + 1 < length) {
                setSpan(
                    RelativeSizeSpan(doubleLineClockStyle.dateScale),
                    firstLineEnd + 1,
                    length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
'''
new = '''            if (firstLineEnd + 1 < length) {
                val secondLineStart = firstLineEnd + 1
                setSpan(
                    RelativeSizeSpan(doubleLineClockStyle.dateScale),
                    secondLineStart,
                    length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                if (Build.MODEL.startsWith("SM-F966", ignoreCase = true)) {
                    // Keep the existing single-TextView layout. Move only the
                    // second line's baseline upward by 1dp, leaving the scale
                    // ranges, measured height, and view hierarchy unchanged.
                    val baselineShiftPx = maxOf(1, (1f * density).roundToInt())
                    setSpan(
                        Fold7SecondLineBaselineSpan(baselineShiftPx),
                        secondLineStart,
                        length,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            }
'''
if old not in s:
    raise SystemExit("Second-line RelativeSizeSpan block not found; no source changed")
s = s.replace(old, new, 1)

path.write_text(s, encoding="utf-8")
check = path.read_text(encoding="utf-8")
assert "Fold7SecondLineBaselineSpan" in check
assert "textPaint.baselineShift -= shiftPx" in check
assert "baselineShiftPx = maxOf(1, (1f * density).roundToInt())" in check
print("Verified: Fold7 second-line baseline-only test patch")
