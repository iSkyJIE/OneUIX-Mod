#!/usr/bin/env python3
"""One-time upstream v1.8.0 network threshold merge into existing MOD files."""
from pathlib import Path

root = Path('app/src/main/java/io/github/soclear/oneuix')
changes = {
    root / 'hook/Network.kt': [
        ('        intervalMillisecond: Long = 3000L\n    ) {', '        intervalMillisecond: Long = 3000L,\n        thresholdKb: Int = 0\n    ) {'),
        ('                val rxBytesPerSecond = (current.totalRx - previous.totalRx) / actualIntervalSeconds\n                return', '                val rxBytesPerSecond = (current.totalRx - previous.totalRx) / actualIntervalSeconds\n                if (thresholdKb > 0 && txBytesPerSecond <= thresholdKb * 1024f &&\n                    rxBytesPerSecond <= thresholdKb * 1024f) return ""\n                return'),
    ],
    root / 'hook/Main.kt': [
        ('                    Network.showSeparateUpDownNetworkSpeeds(lpparam)', '                    Network.showSeparateUpDownNetworkSpeeds(\n                        lpparam,\n                        thresholdKb = preference.systemUI.statusBar.networkSpeedThresholdKb\n                    )'),
    ],
}

# Validate every anchor and existing preference before writing anything.
pref = (root / 'data/Preference.kt').read_text()
assert 'val networkSpeedThresholdKb: Int = 0,' in pref, 'Preference field missing'
prepared = {}
for path, replacements in changes.items():
    content = path.read_text()
    for before, after in replacements:
        count = content.count(before)
        if count != 1:
            raise RuntimeError(f'{path}: expected exactly one anchor, found {count}: {before!r}')
        content = content.replace(before, after, 1)
    prepared[path] = content
for path, content in prepared.items():
    path.write_text(content)
    print(f'Updated {path}')
