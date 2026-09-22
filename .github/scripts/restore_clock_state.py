from pathlib import Path
p = Path('hook/src/main/java/io/github/soclear/oneuix/hook/systemui/StatusBar.kt')
s = p.read_text()
anchor = 'import android.widget.TextView\n'
assert s.count(anchor) == 1
s = s.replace(anchor, anchor + 'import android.text.TextUtils\nimport java.util.WeakHashMap\n', 1)
anchor = 'object StatusBar {\n'
assert s.count(anchor) == 1
s = s.replace(anchor, anchor + '''    private data class OriginalClockTextState(
        val includeFontPadding: Boolean,
        val ellipsize: TextUtils.TruncateAt?,
    )

    private val originalClockTextStates = WeakHashMap<TextView, OriginalClockTextState>()

''', 1)
a = s.index('                // Match the MOD two-line text behavior while preserving the upstream single-line path.')
b = s.index('                clockTextView?.text = dateTime', a)
old = s[a:b]
assert old.count('setSingleLine(false)') == 1 and old.count('setSingleLine(true)') == 1
replacement = '''                clockTextView?.apply {
                    if (dateTime.indexOf(10.toChar()) >= 0) {
                        if (!originalClockTextStates.containsKey(this)) {
                            originalClockTextStates[this] = OriginalClockTextState(
                                includeFontPadding = includeFontPadding,
                                ellipsize = ellipsize,
                            )
                        }
                        setSingleLine(false)
                        maxLines = 2
                        minLines = 2
                        includeFontPadding = false
                        ellipsize = null
                        setHorizontallyScrolling(false)
                    } else {
                        setSingleLine(true)
                        maxLines = 1
                        minLines = 1
                        originalClockTextStates.remove(this)?.let { original ->
                            includeFontPadding = original.includeFontPadding
                            ellipsize = original.ellipsize
                        }
                        setLineSpacing(0f, 1f)
                    }
                }
'''
s = s[:a] + replacement + s[b:]
p.write_text(s)
