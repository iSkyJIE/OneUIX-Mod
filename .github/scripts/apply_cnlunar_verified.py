from pathlib import Path
import re

ROOT = Path(".")
STATUSBAR = ROOT / "app/src/main/java/io/github/soclear/oneuix/hook/systemui/StatusBar.kt"
DETAIL = ROOT / "app/src/main/java/io/github/soclear/oneuix/ui/category/DetailPaneSystemUI.kt"
IMPORT = "import io.github.soclear.oneuix.hook.util.StatusBarClockFormatter\n"

status = STATUSBAR.read_text(encoding="utf-8")
if "import io.github.soclear.oneuix.hook.util.StatusBarClockFormatter" not in status:
    marker = "import io.github.soclear.oneuix.hook.util.PreferenceProvider\n"
    if marker not in status:
        raise SystemExit("Missing StatusBar PreferenceProvider import")
    status = status.replace(marker, marker + IMPORT, 1)

# Replace only the formatter selection inside setStatusBarClockFormat().
old_runtime = re.compile(
    r"        val dateTimeFormatter = try \{\n"
    r"            DateTimeFormatter\.ofPattern\(format\)\n"
    r"        \} catch \(_:\ Throwable\) \{\n"
    r"            DateTimeFormatter\.ofPattern\(\"HH:mm\"\)\n"
    r"        \}\n"
    r"        updateDoubleLineClockRuntimeConfig\(\n"
    r"            doubleLineClockSize,\n"
    r"            legacyDoubleLinePresetScale,\n"
    r"            doubleLineClockGapDp,\n"
    r"            useFold7CustomScale,\n"
    r"            fold7TimeScale,\n"
    r"            fold7DateScale,\n"
    r"        \)\n"
    r"        observeStatusBarPreference\(\)\n"
    r"        setStatusBarClockText\(\n"
    r"            loadPackageParam,\n"
    r"        \) \{\n"
    r"            dateTimeFormatter\.format\(LocalDateTime\.now\(\)\)\n"
    r"        \}",
    re.S,
)
new_runtime = '''        updateDoubleLineClockRuntimeConfig(
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
            runCatching {
                StatusBarClockFormatter.format(format, LocalDateTime.now())
            }.getOrElse {
                DateTimeFormatter.ofPattern("HH:mm").format(LocalDateTime.now())
            }
        }'''
if "StatusBarClockFormatter.format(format, LocalDateTime.now())" not in status:
    status, count = old_runtime.subn(new_runtime, status, count=1)
    if count != 1:
        raise SystemExit("Runtime block did not match; no source changed")
STATUSBAR.write_text(status, encoding="utf-8")

# Preview: use the same formatter and therefore the same custom grammar.
detail = DETAIL.read_text(encoding="utf-8")
if "import io.github.soclear.oneuix.hook.util.StatusBarClockFormatter" not in detail:
    marker = "import io.github.soclear.oneuix.ui.component.SwitchItem\n"
    if marker not in detail:
        raise SystemExit("Missing DetailPane SwitchItem import")
    detail = detail.replace(marker, marker + IMPORT, 1)

old_preview = re.compile(
    r"                            label = try \{\n"
    r"                                DateTimeFormatter\n"
    r"                                    \.ofPattern\(tempDataTimeFormat\)\n"
    r"                                    \.format\(LocalDateTime\.now\(\)\)\n"
    r"                            \} catch \(_:\ Throwable\) \{\n"
    r"                                right = false\n"
    r"                                \"error\"\n"
    r"                            \}",
    re.S,
)
new_preview = '''                            label = try {
                                StatusBarClockFormatter.format(
                                    tempDataTimeFormat,
                                    LocalDateTime.now()
                                )
                            } catch (_: Throwable) {
                                right = false
                                "error"
                            }'''
if "StatusBarClockFormatter.format(" not in detail:
    detail, count = old_preview.subn(new_preview, detail, count=1)
    if count != 1:
        raise SystemExit("Preview block did not match; no source changed")
DETAIL.write_text(detail, encoding="utf-8")

# Hard verification: the build must fail if either patch is absent.
status_check = STATUSBAR.read_text(encoding="utf-8")
detail_check = DETAIL.read_text(encoding="utf-8")
assert "StatusBarClockFormatter.format(format, LocalDateTime.now())" in status_check
assert "StatusBarClockFormatter.format(" in detail_check
print("Verified: custom clock formatter runtime + preview patched")