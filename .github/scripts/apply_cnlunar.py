from pathlib import Path

path = Path("app/src/main/java/io/github/soclear/oneuix/hook/systemui/StatusBar.kt")
text = path.read_text(encoding="utf-8")

if "formatStatusBarClock" in text:
    print("CNLUNAR parser already present")
    raise SystemExit(0)

imp = "import io.github.soclear.oneuix.hook.util.PreferenceProvider\n"
new_imp = imp + "import io.github.soclear.oneuix.hook.util.TraditionalChineseCalendar\n"
if imp not in text:
    raise SystemExit("PreferenceProvider import not found")
text = text.replace(imp, new_imp, 1)

marker = "    fun setStatusBarClockFormat(\n"
helper = '''    private fun formatStatusBarClock(format: String, now: LocalDateTime): String {
        if (!format.contains("CNLUNAR")) {
            return try {
                DateTimeFormatter.ofPattern(format).format(now)
            } catch (_: Throwable) {
                DateTimeFormatter.ofPattern("HH:mm").format(now)
            }
        }

        val lunarText = TraditionalChineseCalendar.getMonthAndDay(now.toLocalDate())
        return try {
            val parts = format.split("CNLUNAR")
            buildString {
                parts.forEachIndexed { index, part ->
                    if (part.isNotEmpty()) {
                        append(DateTimeFormatter.ofPattern(part).format(now))
                    }
                    if (index < parts.lastIndex) {
                        append(lunarText)
                    }
                }
            }
        } catch (_: Throwable) {
            DateTimeFormatter.ofPattern("HH:mm").format(now)
        }
    }

'''
if marker not in text:
    raise SystemExit("setStatusBarClockFormat marker not found")
text = text.replace(marker, helper + marker, 1)

old = '''        val dateTimeFormatter = try {
            DateTimeFormatter.ofPattern(format)
        } catch (_: Throwable) {
            DateTimeFormatter.ofPattern("HH:mm")
        }
'''
if old not in text:
    raise SystemExit("dateTimeFormatter block not found")
text = text.replace(old, "", 1)

old_call = '''        ) {
            dateTimeFormatter.format(LocalDateTime.now())
        }
'''
new_call = '''        ) {
            formatStatusBarClock(format, LocalDateTime.now())
        }
'''
if old_call not in text:
    raise SystemExit("clock formatter call not found")
text = text.replace(old_call, new_call, 1)

path.write_text(text, encoding="utf-8")
print("CNLUNAR test patch applied")
