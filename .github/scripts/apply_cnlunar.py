from pathlib import Path
import re

ROOT = Path(".")
STATUSBAR = ROOT / "app/src/main/java/io/github/soclear/oneuix/hook/systemui/StatusBar.kt"
DETAIL = ROOT / "app/src/main/java/io/github/soclear/oneuix/ui/category/DetailPaneSystemUI.kt"

formatter_import = "import io.github.soclear.oneuix.hook.util.StatusBarClockFormatter\n"

# StatusBar runtime: use the same formatter that the settings preview uses.
status_text = STATUSBAR.read_text(encoding="utf-8")
if "StatusBarClockFormatter" not in status_text:
    marker = "import io.github.soclear.oneuix.hook.util.PreferenceProvider\n"
    if marker not in status_text:
        raise SystemExit("StatusBar PreferenceProvider import not found")
    status_text = status_text.replace(marker, marker + formatter_import, 1)

old_formatter = '''        val dateTimeFormatter = try {
            DateTimeFormatter.ofPattern(format)
        } catch (_: Throwable) {
            DateTimeFormatter.ofPattern("HH:mm")
        }
'''
if old_formatter in status_text:
    status_text = status_text.replace(old_formatter, "", 1)

old_call = '''        ) {
            dateTimeFormatter.format(LocalDateTime.now())
        }
'''
new_call = '''        ) {
            runCatching {
                StatusBarClockFormatter.format(format, LocalDateTime.now())
            }.getOrElse {
                DateTimeFormatter.ofPattern("HH:mm").format(LocalDateTime.now())
            }
        }
'''
if old_call in status_text:
    status_text = status_text.replace(old_call, new_call, 1)

STATUSBAR.write_text(status_text, encoding="utf-8")

# Settings preview/validation: validate the exact same grammar as runtime.
detail_text = DETAIL.read_text(encoding="utf-8")
if "StatusBarClockFormatter" not in detail_text:
    marker = "import io.github.soclear.oneuix.ui.component.SwitchItem\n"
    if marker not in detail_text:
        raise SystemExit("DetailPane SwitchItem import not found")
    detail_text = detail_text.replace(marker, marker + formatter_import, 1)

old_preview = '''                            label = try {
                                DateTimeFormatter
                                    .ofPattern(tempDataTimeFormat)
                                    .format(LocalDateTime.now())
                            } catch (_: Throwable) {
                                right = false
                                "error"
                            }
'''
new_preview = '''                            label = try {
                                StatusBarClockFormatter.format(
                                    tempDataTimeFormat,
                                    LocalDateTime.now()
                                )
                            } catch (_: Throwable) {
                                right = false
                                "error"
                            }
'''
if old_preview in detail_text:
    detail_text = detail_text.replace(old_preview, new_preview, 1)
else:
    raise SystemExit("Clock preview block not found")

DETAIL.write_text(detail_text, encoding="utf-8")
print("CNLUNAR runtime and preview test patch applied")