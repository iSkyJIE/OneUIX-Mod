from pathlib import Path

root = Path('app/src/main')
ui = root / 'java/io/github/soclear/oneuix/ui/category/DetailPaneSystemUI.kt'
changes = {}

def replace_once(text, old, new, name):
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f'{name}: expected one anchor, found {count}')
    return text.replace(old, new, 1)

text = ui.read_text(encoding='utf-8')
anchor = '''        Column {
            var expanded by rememberSaveable { mutableStateOf(false) }

            SwitchItem(
                title = stringResource(id = R.string.setStatusBarClockFormat_title),'''
addition = '''        Column {
            var expanded by rememberSaveable { mutableStateOf(false) }
            var threshold by remember { mutableIntStateOf(uiState.statusBar.networkSpeedThresholdKb) }
            SwitchItem(
                icon = ImageVector.vectorResource(id = R.drawable.net_speed),
                title = stringResource(id = R.string.networkSpeedThreshold_title),
                summary = if (uiState.statusBar.networkSpeedThresholdKb > 0) "${uiState.statusBar.networkSpeedThresholdKb} KB/s" else null,
                modifier = Modifier.animateContentSize(),
                clickable = true,
                onClick = { expanded = !expanded },
                checked = uiState.statusBar.networkSpeedThresholdKb > 0,
                onCheckedChange = {
                    if (it && threshold == 0) threshold = 1
                    if (!it) threshold = 0
                    onEvent(SystemUIEvent.StatusBar.NetworkSpeedThreshold(threshold))
                }
            )
            AnimatedVisibility(expanded && uiState.statusBar.networkSpeedThresholdKb > 0) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp)) {
                    OutlinedTextField(
                        value = threshold.toString(),
                        onValueChange = { threshold = it.toIntOrNull()?.coerceAtLeast(1) ?: 1 },
                        modifier = Modifier.weight(1f),
                        label = { Text(stringResource(id = R.string.networkSpeedThreshold_label)) }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { onEvent(SystemUIEvent.StatusBar.NetworkSpeedThreshold(threshold)) }) {
                        Text(text = stringResource(id = R.string.confirm))
                    }
                }
            }
        }
        Column {
            var expanded by rememberSaveable { mutableStateOf(false) }

            SwitchItem(
                title = stringResource(id = R.string.setStatusBarClockFormat_title),'''
text = replace_once(text, anchor, addition, 'threshold UI before clock')
text = replace_once(text, '''        value class ShowSeparateUpDownNetworkSpeeds(val value: Boolean) : StatusBar
''', '''        value class ShowSeparateUpDownNetworkSpeeds(val value: Boolean) : StatusBar

        @JvmInline
        value class NetworkSpeedThreshold(val value: Int) : StatusBar
''', 'threshold event')
anchor = '''            is SystemUIEvent.StatusBar.SetStatusBarClockFormat -> {'''
addition = '''            is SystemUIEvent.StatusBar.NetworkSpeedThreshold -> {
                preference.copy(
                    systemUI = preference.systemUI.copy(
                        statusBar = preference.systemUI.statusBar.copy(
                            networkSpeedThresholdKb = event.value.coerceAtLeast(0)
                        )
                    )
                )
            }

''' + anchor
text = replace_once(text, anchor, addition, 'threshold reducer')
changes[ui] = text

for locale, title, label, confirm in [
    ('values', 'Network speed threshold', 'Threshold (KB/s)', 'Confirm'),
    ('values-zh', '网速显示阈值', '阈值 (KB/s)', '确定'),
]:
    path = root / 'res' / locale / 'strings.xml'
    text = path.read_text(encoding='utf-8')
    if 'name="networkSpeedThreshold_title"' in text or 'name="networkSpeedThreshold_label"' in text:
        raise RuntimeError(f'{path}: threshold strings already present; review manually')
    entries = f'    <string name="networkSpeedThreshold_title">{title}</string>\n    <string name="networkSpeedThreshold_label">{label}</string>\n'
    if 'name="confirm"' not in text:
        entries += f'    <string name="confirm">{confirm}</string>\n'
    text = replace_once(text, '</resources>', entries + '</resources>', f'{locale} strings')
    changes[path] = text

for path, contents in changes.items():
    path.write_text(contents, encoding='utf-8')
    print('Updated', path)
