#!/usr/bin/env python3
# UralVPN: (1) the icons of the subscription card follow the theme (white on dark, dark on light),
# (2) the Ghostty/terminal config item is removed from the app settings,
# (3) a theme switch (light / dark / automatic) in the settings list.
# Usage: patch_theme.py <sfa-root>
import sys

root = sys.argv[1]
base = root + "/app/src/main/java/io/nekohasekai/sfa/compose/screen/"

# 1) icons
home = base + "dashboard/UralHomeScreen.kt"
h = open(home).read()
n = h.count("tint = Color.White")
assert n >= 4, "white icon tints not found: %d" % n
h = h.replace("tint = Color.White", "tint = MaterialTheme.colorScheme.onSurface")
assert h.count("else Color.White,") == 1, "sort icon tint not found"
h = h.replace("else Color.White,", "else MaterialTheme.colorScheme.onSurface,")
assert "Color.White," not in h.replace("Color.White.copy", "x"), "unexpected Color.White left"
open(home, "w").write(h)
print("card icons follow the theme: %d" % n)

# 2) no Ghostty / terminal config
app = base + "settings/AppSettingsScreen.kt"
a = open(app).read()
key = "text = stringResource(R.string.tailscale),"
assert a.count(key) == 1, "tailscale header not found"
k = a.index(key)
start = a.rindex("        Text(\n", 0, k)
end_marker = "        Spacer(modifier = Modifier.height(8.dp))\n\n        Text(\n            text = stringResource(R.string.notification_settings),"
end = a.index(end_marker, k)
block = a[start:end]
assert "settings/tailscale/terminal_config" in block and block.count("ListItem(") == 1, "unexpected block"
a = a[:start] + a[end:]
open(app, "w").write(a)
print("terminal config item removed")

# 3) theme switch
st = base + "settings/SettingsScreen.kt"
s = open(st).read()
anchor = 'headlineContent = { Text("Приложения (обход VPN)"'
assert s.count(anchor) == 1, "bypass item not found"
i = s.index(anchor)
j = s.rindex("                ListItem(\n", 0, i)
item = '''                ListItem(
                    headlineContent = { Text("Тема", style = MaterialTheme.typography.bodyLarge) },
                    supportingContent = {
                        androidx.compose.foundation.layout.Row(
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 6.dp),
                        ) {
                            listOf(1 to "Светлая", 2 to "Тёмная", 0 to "Авто").forEach { (value, label) ->
                                androidx.compose.material3.FilterChip(
                                    selected = io.nekohasekai.sfa.compose.theme.UralThemeMode.mode == value,
                                    onClick = { io.nekohasekai.sfa.compose.theme.UralThemeMode.mode = value },
                                    label = { Text(label) },
                                )
                            }
                        }
                    },
                    leadingContent = {
                        Icon(Icons.Outlined.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
'''
s = s[:j] + item + s[j:]
open(st, "w").write(s)
print("theme switch added")

# 4) the theme mode (0 automatic, 1 light, 2 dark) is kept in the "ural_ui" preferences
th = root + "/app/src/main/java/io/nekohasekai/sfa/compose/theme/Theme.kt"
t = open(th).read()
old = "    darkTheme: Boolean = isSystemInDarkTheme(),\n"
assert t.count(old) == 1, "darkTheme parameter not found"
t = t.replace(old, "    darkTheme: Boolean = when (UralThemeMode.mode) {\n        1 -> false\n        2 -> true\n        else -> isSystemInDarkTheme()\n    },\n")
t += '''
object UralThemeMode {
    private val prefs get() = io.nekohasekai.sfa.Application.application.getSharedPreferences("ural_ui", android.content.Context.MODE_PRIVATE)
    private var state by androidx.compose.runtime.mutableStateOf(-1)

    var mode: Int
        get() {
            if (state < 0) state = prefs.getInt("theme_mode", 0)
            return state
        }
        set(value) {
            state = value
            prefs.edit().putInt("theme_mode", value).apply()
        }
}
'''
t = t.replace("import androidx.compose.runtime.staticCompositionLocalOf\n", "import androidx.compose.runtime.staticCompositionLocalOf\nimport androidx.compose.runtime.getValue\nimport androidx.compose.runtime.setValue\n", 1)
open(th, "w").write(t)
print("theme mode added")
