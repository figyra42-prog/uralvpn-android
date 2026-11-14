#!/usr/bin/env python3
# UralVPN: the app selection screen "Приложения (обход VPN)".
# - the title is "Приложения (обход VPN)" (on both screens);
# - a visible switch of the mode under the title (before it was hidden in the three-dots menu):
#   "Выбранные идут мимо VPN" (exclude) / "Только выбранные идут через VPN" (include);
# - the mode descriptions are in Russian.
# Usage: patch_perapp.py <sfa-root>
import sys

root = sys.argv[1]
base = root + "/app/src/main/java/io/nekohasekai/sfa/compose/screen/"


def swap(text, old, new, what):
    assert text.count(old) == 1, "%s not found (%d)" % (what, text.count(old))
    return text.replace(old, new)


TITLE = "Приложения (обход VPN)"

# 1) screen with the list of apps
p = base + "profileoverride/PerAppProxyScreen.kt"
s = open(p).read()
s = swap(s, "title = { Text(stringResource(R.string.per_app_proxy)) },", 'title = { Text("%s") },' % TITLE, "app list title")
s = swap(
    s,
    "                    stringResource(R.string.per_app_proxy_mode_include_description)\n",
    '                    "Через VPN пойдут только выбранные приложения, всё остальное напрямую"\n',
    "include description",
)
s = swap(
    s,
    "                    stringResource(R.string.per_app_proxy_mode_exclude_description)\n",
    '                    "Выбранные приложения пойдут мимо VPN, всё остальное через VPN"\n',
    "exclude description",
)
anchor = "        AnimatedVisibility(\n            visible = isSearchActive,\n"
s = swap(
    s,
    anchor,
    """        androidx.compose.foundation.layout.Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        ) {
            listOf(
                Settings.PER_APP_PROXY_EXCLUDE to "Выбранные мимо VPN",
                Settings.PER_APP_PROXY_INCLUDE to "Только выбранные через VPN",
            ).forEach { (value, label) ->
                androidx.compose.material3.FilterChip(
                    selected = proxyMode == value,
                    onClick = {
                        if (proxyMode != value) {
                            proxyMode = value
                            coroutineScope.launch {
                                withContext(Dispatchers.IO) {
                                    Settings.perAppProxyMode = value
                                }
                                notifyApplyChange(UiEvent.ApplyServiceChange.Mode.Reload)
                            }
                        }
                    },
                    label = { Text(label, maxLines = 1) },
                )
            }
        }

"""
    + anchor,
    "search animated visibility",
)
open(p, "w").write(s)
print("app list: title, descriptions, visible mode switch")

# 2) settings entry screen
p = base + "settings/ProfileOverrideScreen.kt"
s = open(p).read()
s = swap(s, "title = { Text(stringResource(R.string.profile_override)) },", 'title = { Text("%s") },' % TITLE, "override title")
s = swap(s, "            text = stringResource(R.string.per_app_proxy),\n            style = MaterialTheme.typography.labelLarge,", '            text = "Выбор приложений",\n            style = MaterialTheme.typography.labelLarge,', "section label")
open(p, "w").write(s)
print("override screen: title")

# 3) no "Автоматическое перенаправление" and no "Управляемый режим": only the switch of the app selection,
#    the list of apps and the mode stay (the removed items are root / China-apps tools, not used by UralVPN)
p = base + "settings/ProfileOverrideScreen.kt"
s = open(p).read()
start = s.index("        // Card 1: Auto Redirect\n")
end = s.index("        // Section: Per-App Proxy\n")
assert start < end and "auto_redirect" in s[start:end] and s[start:end].count("Card(") == 1, "auto redirect card"
s = s[:start] + s[end:]
start = s.index("\n                    // Managed Mode toggle\n")
end = s.index("                }\n            }\n        }\n\n        // Shizuku dialog")
assert start < end and "per_app_proxy_managed_mode" in s[start:end] and s[start:end].count("ListItem(") == 1, "managed mode toggle"
s = s[:start] + "\n" + s[end:]
s = swap(s, "                    val manageEnabled = !managedModeEnabled\n", "                    val manageEnabled = true\n", "manage enabled")
s = swap(
    s,
    "                        Modifier.clickable(enabled = manageEnabled) {\n",
    "                        Modifier.clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)).clickable(enabled = manageEnabled) {\n",
    "manage entry modifier",
)
s = swap(
    s,
    "    var managedModeEnabled by remember { mutableStateOf(Settings.perAppProxyManagedMode) }\n",
    "    var managedModeEnabled by remember { mutableStateOf(false) }\n",
    "managed mode state",
)
open(p, "w").write(s)
print("override screen: auto redirect and managed mode removed")
