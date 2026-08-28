from pathlib import Path

path = Path("app/src/main/java/io/github/soclear/oneuix/hook/systemui/StatusBar.kt")
s = path.read_text(encoding="utf-8")

needle = '''        clockTextView.contentDescription = dateTime.replace('\\n', ' ')
        clockTextView.requestLayout()
        clockTextView.invalidate()
'''
replacement = '''        clockTextView.contentDescription = dateTime.replace('\\n', ' ')
        clockTextView.requestLayout()
        clockTextView.invalidate()
        // Fold7 safety test: after Samsung's clock refresh/layout pass, restore the
        // double-line TextView's vertical gravity and our intentional optical offset.
        // Keep the original single-TextView architecture and do not alter its height.
        clockTextView.postOnAnimation {
            if (!clockTextView.isAttachedToWindow) return@postOnAnimation
            clockTextView.gravity =
                (clockTextView.gravity and Gravity.VERTICAL_GRAVITY_MASK.inv()) or
                    Gravity.CENTER_VERTICAL
            clockTextView.translationY = doubleLineClockStyle.opticalTranslationYDp * density
        }
'''
if needle not in s:
    raise SystemExit("Expected clock refresh tail not found; no source changed")
s = s.replace(needle, replacement, 1)
path.write_text(s, encoding="utf-8")
check = path.read_text(encoding="utf-8")
assert "postOnAnimation" in check
assert "Gravity.CENTER_VERTICAL" in check
assert "doubleLineClockStyle.opticalTranslationYDp * density" in check
print("Verified: Fold7 refresh-center patch")
